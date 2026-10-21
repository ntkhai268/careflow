package com.careflow.emr.controller;

import java.util.HashMap;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.ActiveSessionFilter;
import com.careflow.common.security.ActiveSessionClient;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.GlobalExceptionHandler;
import com.careflow.emr.client.ConsultationClient;
import com.careflow.emr.client.LabClient;
import com.careflow.emr.client.PatientClient;
import com.careflow.emr.client.PrescriptionClient;
import com.careflow.emr.mapper.MedicalRecordMapper;
import com.careflow.emr.repository.MedicalRecordRepository;
import com.careflow.emr.service.EmrAccessService;
import com.careflow.emr.service.MedicalRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmrAuthorizationRegressionTest {
    // Authorization fixture only: production introspection is exercised by real HTTPS E2E tests.
    private final ActiveSessionClient sessions = mock(ActiveSessionClient.class);
    private final Map<String, ActiveSessionClient.Principal> activeSessions = new HashMap<>();

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private final MedicalRecordRepository repository = mock(MedicalRecordRepository.class);
    private final PatientClient patients = mock(PatientClient.class);
    private final ConsultationClient consultations = mock(ConsultationClient.class);
    private final PrescriptionClient prescriptions = mock(PrescriptionClient.class);
    private final LabClient labs = mock(LabClient.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(sessions.verifyRequest(any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            ActiveSessionClient.Principal principal = activeSessions.get(invocation.getArgument(0));
            if (principal == null || !principal.expiresAt().isAfter(Instant.now())) {
                throw new BusinessException(401, "Inactive or invalid Identity session");
            }
            return principal;
        });
        MedicalRecordService service = new MedicalRecordService(repository, new MedicalRecordMapper(), patients,
                consultations, prescriptions, labs);
        mvc = MockMvcBuilders.standaloneSetup(new MedicalRecordController(service, new EmrAccessService(patients)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new ActiveSessionFilter(sessions, new ObjectMapper().findAndRegisterModules())).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"READ", "SUMMARY", "CREATE", "UPDATE"})
    void everyEmrOperationRequiresJwtBeforeFetchingData(String operation) throws Exception {
        mvc.perform(operation(operation).header("X-User-Id", OWNER).header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, patients, consultations, prescriptions, labs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "LAB_TECHNICIAN", "ADMIN"})
    void operationalRolesCannotReadFullSummary(String role) throws Exception {
        mvc.perform(operation("SUMMARY").header("Authorization", "Bearer " + token(OWNER, role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository, patients, consultations, prescriptions, labs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"READ", "SUMMARY"})
    void ownerMismatchNeverReadsMasterOrDownstreamClinicalData(String operation) throws Exception {
        when(patients.getPatientById(PATIENT)).thenReturn(ApiResponse.success(
                Map.of("id", PATIENT.toString(), "userId", OWNER.toString())));
        mvc.perform(operation(operation).header("Authorization", "Bearer " + token(UUID.randomUUID(), "PATIENT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository, consultations, prescriptions, labs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "UPDATE"})
    void patientsCannotChangeOfficialMedicalRecords(String operation) throws Exception {
        mvc.perform(operation(operation).header("Authorization", "Bearer " + token(OWNER, "PATIENT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository, patients, consultations, prescriptions, labs);
    }

    @Test
    void unassignedDoctorIsRejectedByPatientAuthorityBeforeReadingEmr() throws Exception {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(403);
        when(patients.getPatientById(PATIENT)).thenThrow(exception);
        mvc.perform(operation("SUMMARY").header("Authorization", "Bearer " + token(UUID.randomUUID(), "DOCTOR")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository, consultations, prescriptions, labs);
    }

    @Test
    void unavailablePatientAuthorityNeverGrantsAccess() throws Exception {
        when(patients.getPatientById(PATIENT)).thenThrow(new IllegalStateException("unavailable"));
        mvc.perform(operation("SUMMARY").header("Authorization", "Bearer " + token(OWNER, "PATIENT")))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(repository, consultations, prescriptions, labs);
    }

    @Test
    void readingSummaryDoesNotCreateMissingMasterRecord() throws Exception {
        when(patients.getPatientById(PATIENT)).thenReturn(ApiResponse.success(
                Map.of("id", PATIENT.toString(), "userId", OWNER.toString(), "fullName", "Audit Patient")));
        when(repository.findByPatientId(PATIENT)).thenReturn(Optional.empty());
        when(consultations.getConsultationsByPatient(PATIENT)).thenReturn(ApiResponse.success(List.of()));
        when(prescriptions.getPrescriptionsByPatient(PATIENT)).thenReturn(ApiResponse.success(List.of()));
        when(labs.getLabOrdersByPatient(PATIENT)).thenReturn(ApiResponse.success(List.of()));
        mvc.perform(operation("SUMMARY").header("Authorization", "Bearer " + token(OWNER, "PATIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.patient.id").value(PATIENT.toString()));
        verify(repository, never()).save(any());
        verify(repository, never()).count();
    }

    @Test
    void providerFailureIsNotReportedAsEmptyClinicalHistory() throws Exception {
        when(patients.getPatientById(PATIENT)).thenReturn(ApiResponse.success(
                Map.of("id", PATIENT.toString(), "userId", OWNER.toString())));
        when(repository.findByPatientId(PATIENT)).thenReturn(Optional.empty());
        when(consultations.getConsultationsByPatient(PATIENT)).thenThrow(new IllegalStateException("unavailable"));
        mvc.perform(operation("SUMMARY").header("Authorization", "Bearer " + token(OWNER, "PATIENT")))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(prescriptions, labs);
        verify(repository, never()).save(any());
    }

    @Test
    void labClientUsesTheSamePatientOrdersEndpointAsMobile() throws Exception {
        var annotation = LabClient.class.getMethod("getLabOrdersByPatient", UUID.class)
                .getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
        assertThat(annotation.value()).containsExactly("/api/labs/orders/patient/{patientId}");
    }

    private MockHttpServletRequestBuilder operation(String operation) {
        String base = "/api/emr/patients/" + PATIENT;
        return switch (operation) {
            case "CREATE" -> post("/api/emr/records").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"patientId\":\"" + PATIENT + "\",\"bloodType\":\"Audit\"}");
            case "UPDATE" -> put(base + "/record").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bloodType\":\"Audit\"}");
            case "SUMMARY" -> get(base + "/summary");
            default -> get(base + "/record");
        };
    }

    private String token(UUID userId, String role) {
        String token = UUID.randomUUID().toString();
        activeSessions.put("Bearer " + token, new ActiveSessionClient.Principal(true, userId, role,
                UUID.randomUUID(), Instant.now().plusSeconds(60)));
        return token;
    }
}
