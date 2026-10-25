package com.careflow.lab.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.lab.client.LabConsultationClient;
import com.careflow.lab.client.PatientIdentityClient;
import com.careflow.lab.client.QueueExecutionClient;
import com.careflow.lab.dto.request.UpdateLabResultRequest;
import com.careflow.lab.model.*;
import com.careflow.lab.repository.LabOrderItemRepository;
import com.careflow.lab.repository.LabOrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabAccessServiceTest {
    private static final UUID PATIENT = UUID.fromString("f1000000-0000-0000-0000-000000000002");
    private static final UUID OWNER = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID DOCTOR = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final UUID CONSULTATION = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID ORDER = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID TECH = UUID.fromString("44000000-0000-0000-0000-000000000001");
    @Mock LabConsultationClient consultations;
    @Mock PatientIdentityClient patients;
    @Mock QueueExecutionClient queues;
    @Mock LabOrderRepository orders;
    @Mock LabOrderItemRepository items;
    @Mock LabEventPublisher events;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private LabAccessService access;
    private LabOrderService service;
    private LabCatalogService catalog;

    @BeforeEach
    void setUp() {
        catalog = mock(LabCatalogService.class);
        lenient().when(catalog.requireItem(any())).thenAnswer(invocation -> {
            com.careflow.lab.dto.request.LabOrderItemRequest item = invocation.getArgument(0);
            return new LabCatalogService.Definition(item.serviceCode(), item.serviceName(), item.servicePointId(), item.preparationInstruction());
        });
        access = new LabAccessService(consultations, patients, queues, mock(com.careflow.lab.client.LabDirectoryClient.class));
        service = new LabOrderService(orders, items, events, mapper, access, catalog);
    }

    private String fixture(String provider, String suffix) throws Exception {
        try (var input = new ClassPathResource("contracts/" + provider + "/access-" + suffix + ".json").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ApiResponse<LabConsultationClient.Context> consultation() throws Exception {
        return mapper.readValue(fixture("consultation", "success"), new TypeReference<>() {});
    }

    private ApiResponse<Map<String, Object>> payload(String provider) throws Exception {
        return mapper.readValue(fixture(provider, "success"), new TypeReference<>() {});
    }

    private LabOrder order(LabOrderStatus status) {
        var order = new LabOrder();
        order.setId(ORDER); order.setConsultationId(CONSULTATION); order.setPatientId(PATIENT);
        order.setOrderedByDoctorId(DOCTOR); order.setStatus(status); order.setClinicalNote("Internal ordering note");
        var item = new LabOrderItem(); item.setId(UUID.randomUUID()); item.setOrder(order);
        item.setServicePointId("LAB-HEMATOLOGY-01"); item.setServiceCode("CBC"); item.setServiceName("Synthetic test");
        item.setStatus(LabOrderItemStatus.COMPLETED); item.setResultValue("Synthetic result"); item.setRequired(true);
        order.getItems().add(item);
        return order;
    }

    private void denied(int expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "ADMIN", "PATIENT", "LAB_TECHNICIAN"})
    void nonDoctorsCannotAuthorizeClinicalCommands(String role) {
        denied(403, () -> access.requireConsultation(CONSULTATION, PATIENT, DOCTOR, role, true));
        verifyNoInteractions(consultations);
    }

    @Test
    void assignedDoctorMustMatchExactPatientAndConsultation() throws Exception {
        when(consultations.getById(CONSULTATION)).thenReturn(consultation());
        assertThat(access.requireConsultation(CONSULTATION, PATIENT, DOCTOR, "DOCTOR", true).patientId()).isEqualTo(PATIENT);
        denied(403, () -> access.requireConsultation(CONSULTATION, UUID.randomUUID(), DOCTOR, "DOCTOR", true));
        denied(403, () -> access.requireConsultation(CONSULTATION, PATIENT, UUID.randomUUID(), "DOCTOR", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "CANCELLED", "TRANSFERRED"})
    void closedConsultationCannotReceiveNewOrders(String status) {
        when(consultations.getById(CONSULTATION)).thenReturn(ApiResponse.success(new LabConsultationClient.Context(
                CONSULTATION, PATIENT, DOCTOR, status)));
        denied(409, () -> access.requireConsultation(CONSULTATION, PATIENT, DOCTOR, "DOCTOR", true));
    }

    @Test
    void unknownConsultationStateIsUnavailable() {
        when(consultations.getById(CONSULTATION)).thenReturn(ApiResponse.success(new LabConsultationClient.Context(
                CONSULTATION, PATIENT, DOCTOR, "UNKNOWN")));
        denied(503, () -> access.requireConsultation(CONSULTATION, PATIENT, DOCTOR, "DOCTOR", true));
    }

    @ParameterizedTest
    @CsvSource({"401,401", "403,403", "404,404", "500,503", "503,503"})
    void consultationFailuresRemainDeniedOrUnavailable(int remote, int expected) throws Exception {
        when(consultations.getById(CONSULTATION)).thenThrow(error("consultation", remote));
        denied(expected, () -> access.requireConsultation(CONSULTATION, PATIENT, DOCTOR, "DOCTOR", true));
    }

    @Test
    void secondOwnedProfileIsResolvedByPatientId() throws Exception {
        when(patients.getById(PATIENT, OWNER, "PATIENT")).thenReturn(payload("patient"));
        access.requireProfile(PATIENT, OWNER, "PATIENT");
        verify(patients).getById(PATIENT, OWNER, "PATIENT");
    }

    @Test
    void providerSuccessCannotAuthorizeWrongOwner() throws Exception {
        UUID other = UUID.randomUUID();
        when(patients.getById(PATIENT, other, "PATIENT")).thenReturn(payload("patient"));
        denied(403, () -> access.requireProfile(PATIENT, other, "PATIENT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "ADMIN", "LAB_TECHNICIAN"})
    void operationalRolesCannotReadWholePatientLabHistory(String role) {
        denied(403, () -> service.getByPatient(PATIENT, OWNER, role));
        verifyNoInteractions(patients, orders);
    }

    @ParameterizedTest
    @CsvSource({"401,401", "403,403", "404,404", "500,503", "503,503"})
    void patientFailuresCannotBecomeEmptySuccessfulHistory(int remote, int expected) throws Exception {
        when(patients.getById(PATIENT, OWNER, "PATIENT")).thenThrow(error("patient", remote));
        denied(expected, () -> service.getByPatient(PATIENT, OWNER, "PATIENT"));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @EnumSource(value = LabOrderStatus.class, names = {"ORDERED", "QUEUED", "CALLED", "IN_PROGRESS", "CANCELLED"})
    void patientCannotReadUnpublishedOrder(LabOrderStatus status) throws Exception {
        when(patients.getById(PATIENT, OWNER, "PATIENT")).thenReturn(payload("patient"));
        denied(403, () -> access.requireRead(order(status), OWNER, "PATIENT"));
    }

    @Test
    void patientHistoryFiltersDraftAndHidesInternalClinicalNote() throws Exception {
        when(patients.getById(PATIENT, OWNER, "PATIENT")).thenReturn(payload("patient"));
        var published = order(LabOrderStatus.RESULT_AVAILABLE);
        when(orders.findByPatientIdOrderByCreatedAtDesc(PATIENT)).thenReturn(List.of(published, order(LabOrderStatus.ORDERED)));
        var result = service.getByPatient(PATIENT, OWNER, "PATIENT");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().clinicalNote()).isNull();
        assertThat(published.getClinicalNote()).isEqualTo("Internal ordering note");
    }

    @Test
    void validClaimAllowsTechnicianAtCorrectServicePoint() throws Exception {
        when(queues.getAtServicePoint(ORDER, "LAB-HEMATOLOGY-01", TECH, "LAB_TECHNICIAN")).thenReturn(payload("queue"));
        access.requireTechnician(order(LabOrderStatus.IN_PROGRESS), TECH, "LAB_TECHNICIAN", Set.of("IN_PROGRESS"), "LAB-HEMATOLOGY-01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"entryId", "labOrderId", "patientId", "consultationId", "calledByUserId", "type", "queueStatus", "servicePointId"})
    void incompleteClaimIsUnavailable(String missing) throws Exception {
        var response = payload("queue"); response.getData().remove(missing);
        when(queues.getCurrent(ORDER, TECH, "LAB_TECHNICIAN")).thenReturn(response);
        denied(503, () -> access.requireTechnician(order(LabOrderStatus.IN_PROGRESS), TECH, "LAB_TECHNICIAN", Set.of("IN_PROGRESS"), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"labOrderId", "patientId", "consultationId", "calledByUserId"})
    void claimCannotBindOtherResourceOrTechnician(String wrong) throws Exception {
        var response = payload("queue"); response.getData().put(wrong, UUID.randomUUID().toString());
        when(queues.getCurrent(ORDER, TECH, "LAB_TECHNICIAN")).thenReturn(response);
        denied(403, () -> access.requireTechnician(order(LabOrderStatus.IN_PROGRESS), TECH, "LAB_TECHNICIAN", Set.of("IN_PROGRESS"), null));
    }

    @Test
    void technicianCannotUseClaimForAnotherItemServicePoint() throws Exception {
        when(queues.getAtServicePoint(ORDER, "OTHER-LAB", TECH, "LAB_TECHNICIAN")).thenReturn(payload("queue"));
        denied(403, () -> access.requireTechnician(order(LabOrderStatus.IN_PROGRESS), TECH, "LAB_TECHNICIAN", Set.of("IN_PROGRESS"), "OTHER-LAB"));
    }

    @ParameterizedTest
    @CsvSource({"401,401", "403,403", "404,409", "500,503", "503,503"})
    void queueFailuresNeverAuthorizeResultWrites(int remote, int expected) throws Exception {
        var order = order(LabOrderStatus.IN_PROGRESS); var item = order.getItems().getFirst();
        when(orders.findById(ORDER)).thenReturn(Optional.of(order)); when(items.findById(item.getId())).thenReturn(Optional.of(item));
        when(queues.getAtServicePoint(ORDER, "LAB-HEMATOLOGY-01", TECH, "LAB_TECHNICIAN")).thenThrow(error("queue", remote));
        denied(expected, () -> service.updateResult(ORDER, item.getId(), new UpdateLabResultRequest("Changed", null, null, null, null), TECH, "LAB_TECHNICIAN"));
        assertThat(item.getResultValue()).isEqualTo("Synthetic result"); verify(items, never()).save(any()); verifyNoInteractions(events);
    }

    @Test
    void repeatedStartRechecksClaimBeforeReturningSuccess() throws Exception {
        var order = order(LabOrderStatus.IN_PROGRESS);
        var response = payload("queue"); response.getData().put("calledByUserId", UUID.randomUUID().toString());
        when(orders.findById(ORDER)).thenReturn(Optional.of(order)); when(queues.getCurrent(ORDER, TECH, "LAB_TECHNICIAN")).thenReturn(response);
        denied(403, () -> service.startOrder(ORDER, TECH, "LAB_TECHNICIAN")); verify(orders, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "STAFF", "ADMIN", "PATIENT"})
    void nonTechniciansCannotFinalize(String role) {
        denied(403, () -> service.finalizeOrder(ORDER, DOCTOR, role, "trace"));
        verifyNoInteractions(orders, queues, events);
    }

    private FeignException error(String provider, int status) throws Exception {
        var request = Request.create(Request.HttpMethod.GET, "http://provider.test", Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("access", Response.builder().request(request).status(status).headers(Map.of())
                .body(fixture(provider, "error-" + status), StandardCharsets.UTF_8).build());
    }
}
