package com.careflow.appointment.service;

import com.careflow.appointment.client.*;
import com.careflow.appointment.client.dto.*;
import com.careflow.appointment.dto.request.CreateFollowUpRequest;
import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.model.Department;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentFollowUpAuthorizationTest {
    private static final UUID CONSULTATION = UUID.fromString("c2000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT = UUID.fromString("f2000000-0000-0000-0000-000000000001");
    private static final UUID DOCTOR = UUID.fromString("d2000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    @Mock AppointmentRepository repository;
    @Mock AppointmentEventService events;
    @Mock DirectoryClient directory;
    @Mock PatientClient patients;
    @Mock AppointmentConsultationClient consultations;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(repository, events, AppointmentPricingFixture.priced(directory), patients, null, consultations);
    }

    private CreateFollowUpRequest request() {
        return CreateFollowUpRequest.builder().consultationId(CONSULTATION).patientId(PATIENT)
                .recommendedDate(LocalDate.now().plusDays(14)).department("NOI_TONG_QUAT").timeSlot("09:00-09:30").note("Synthetic follow-up").build();
    }

    private String fixture(String provider, String name) throws Exception {
        try (var input = new ClassPathResource("contracts/" + provider + "/" + name + ".json").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ApiResponse<AppointmentConsultationClient.Context> context() throws Exception {
        return mapper.readValue(fixture("consultation", "followup-success"), new TypeReference<>() {});
    }

    private void authorized() throws Exception {
        when(consultations.getById(CONSULTATION)).thenReturn(context());
        when(patients.getClinicalProfile(PATIENT)).thenReturn(mapper.readValue(fixture("patient", "clinical-success"), new TypeReference<>() {}));
    }

    private void booking() {
        when(directory.getDepartmentByCode("NOI_TONG_QUAT")).thenReturn(ApiResponse.success(DepartmentResponse.builder()
                .id(Department.NOI_TONG_QUAT.getId()).code("NOI_TONG_QUAT").isActive(true).build()));
        when(directory.getAllRooms()).thenReturn(ApiResponse.success(List.of(RoomResponse.builder().id("ROOM-01")
                .departmentCode("NOI_TONG_QUAT").roomType("CONSULTATION").isActive(true).build())));
        when(directory.getDoctors("NOI_TONG_QUAT")).thenReturn(ApiResponse.success(List.of(DoctorProfileResponse.builder()
                .userId(DOCTOR).departmentCode("NOI_TONG_QUAT").assignedRoomId("ROOM-01").isActive(true).build())));
        when(directory.getDoctorByUserId(DOCTOR)).thenReturn(ApiResponse.success(DoctorProfileResponse.builder().userId(DOCTOR).departmentCode("NOI_TONG_QUAT").assignedRoomId("ROOM-01").isActive(true).build()));
        AtomicReference<Appointment> saved = new AtomicReference<>();
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            if (appointment.getId() == null) appointment.setId(UUID.randomUUID());
            saved.set(appointment);
            return appointment;
        });
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
    }

    private void denied(int status, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus()).isEqualTo(status));
    }

    @Test
    void authorizedFollowUpUsesVerifiedPatientAndPublishesOnceOnRetry() throws Exception {
        authorized(); booking();
        var first = service.createFollowUpAppointment(request(), DOCTOR, "trace");
        var repeated = service.createFollowUpAppointment(request(), DOCTOR, "trace-retry");
        assertThat(first.getId()).isEqualTo(repeated.getId());
        assertThat(first.getOwnerUserId()).isEqualTo(OWNER);
        assertThat(first.getPatientName()).isEqualTo("Verified followup patient");
        assertThat(first.getSourceConsultationId()).isEqualTo(CONSULTATION);
        verify(repository, times(2)).saveAndFlush(any());
        verify(events).confirmed(any(), eq("trace"));
        verify(events).followUpScheduled(any(), eq(CONSULTATION), eq("trace"));
        verifyNoMoreInteractions(events);
    }

    @Test
    void cachedFollowUpStillChecksCurrentConsultationAccess() throws Exception {
        authorized(); booking();
        service.createFollowUpAppointment(request(), DOCTOR, "trace");
        when(consultations.getById(CONSULTATION)).thenThrow(error("consultation", "followup", 403));
        denied(403, () -> service.createFollowUpAppointment(request(), DOCTOR, "trace-retry"));
        verify(repository, times(2)).saveAndFlush(any());
        verify(events, times(1)).followUpScheduled(any(), any(), any());
    }

    @Test
    void changedConsultationDoctorCannotReadOldDoctorsCachedBooking() throws Exception {
        authorized(); booking();
        service.createFollowUpAppointment(request(), DOCTOR, "trace");
        UUID nextDoctor = UUID.randomUUID();
        when(consultations.getById(CONSULTATION)).thenReturn(ApiResponse.success(new AppointmentConsultationClient.Context(
                CONSULTATION, PATIENT, nextDoctor, "IN_PROGRESS")));
        denied(409, () -> service.createFollowUpAppointment(request(), nextDoctor, "trace-retry"));
        verify(repository, times(2)).saveAndFlush(any());
        verify(events, times(1)).followUpScheduled(any(), any(), any());
    }

    @Test
    void wrongDoctorAndPatientCannotCreateFollowUp() throws Exception {
        when(consultations.getById(CONSULTATION)).thenReturn(context());
        denied(403, () -> service.createFollowUpAppointment(request(), UUID.randomUUID(), "trace"));
        var different = request(); different.setPatientId(UUID.randomUUID());
        denied(403, () -> service.createFollowUpAppointment(different, DOCTOR, "trace"));
        verifyNoInteractions(patients, repository, events, directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "TRANSFERRED"})
    void cancelledAndTransferredConsultationsCannotScheduleFollowUp(String status) {
        when(consultations.getById(CONSULTATION)).thenReturn(ApiResponse.success(new AppointmentConsultationClient.Context(CONSULTATION, PATIENT, DOCTOR, status)));
        denied(409, () -> service.createFollowUpAppointment(request(), DOCTOR, "trace"));
        verifyNoInteractions(patients, repository, events, directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "patientId", "doctorId", "status"})
    void incompleteContextCannotCreateFollowUp(String missing) throws Exception {
        var json = mapper.readTree(fixture("consultation", "followup-success"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) json.get("data")).remove(missing);
        when(consultations.getById(CONSULTATION)).thenReturn(mapper.readValue(json.toString(), new TypeReference<>() {}));
        denied(503, () -> service.createFollowUpAppointment(request(), DOCTOR, "trace"));
        verifyNoInteractions(patients, repository, events, directory);
    }

    @ParameterizedTest
    @CsvSource({"401,401", "403,403", "404,404", "500,503", "503,503"})
    void consultationFailureDoesNotWriteOrEmit(int remote, int expected) throws Exception {
        when(consultations.getById(CONSULTATION)).thenThrow(error("consultation", "followup", remote));
        denied(expected, () -> service.createFollowUpAppointment(request(), DOCTOR, "trace"));
        verifyNoInteractions(patients, repository, events, directory);
    }

    @ParameterizedTest
    @CsvSource({"401,401", "403,403", "404,404", "500,503", "503,503"})
    void patientFailureDoesNotWriteOrEmit(int remote, int expected) throws Exception {
        when(consultations.getById(CONSULTATION)).thenReturn(context());
        when(patients.getClinicalProfile(PATIENT)).thenThrow(error("patient", "clinical", remote));
        denied(expected, () -> service.createFollowUpAppointment(request(), DOCTOR, "trace"));
        verifyNoInteractions(repository, events, directory);
    }

    @Test
    void patientWithoutOwnerIsExplicitlyUnsupported() throws Exception {
        when(consultations.getById(CONSULTATION)).thenReturn(context());
        when(patients.getClinicalProfile(PATIENT)).thenReturn(ApiResponse.success(new PatientClient.ClinicalProfile(PATIENT, null, "Name")));
        denied(422, () -> service.createFollowUpAppointment(request(), DOCTOR, "trace"));
        verifyNoInteractions(repository, events, directory);
    }

    private FeignException error(String provider, String prefix, int status) throws Exception {
        var req = Request.create(Request.HttpMethod.GET, "http://provider.test", Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("followup", Response.builder().request(req).status(status).headers(Map.of())
                .body(fixture(provider, prefix + "-error-" + status), StandardCharsets.UTF_8).build());
    }
}
