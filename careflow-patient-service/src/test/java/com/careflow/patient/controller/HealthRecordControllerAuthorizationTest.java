package com.careflow.patient.controller;

import java.util.HashMap;
import java.util.Map;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.ActiveSessionFilter;
import com.careflow.patient.security.HealthRecordActorFilter;
import com.careflow.common.security.ActiveSessionClient;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.GlobalExceptionHandler;
import com.careflow.patient.client.AssignmentClient;
import com.careflow.patient.client.dto.AssignmentAccessResponse;
import com.careflow.patient.dto.response.HealthRecordResponse;
import com.careflow.patient.model.Patient;
import com.careflow.patient.repository.HealthRecordFileRepository;
import com.careflow.patient.repository.HealthRecordRepository;
import com.careflow.patient.repository.PatientRepository;
import com.careflow.patient.service.FileStorageService;
import com.careflow.patient.service.HealthRecordAccessService;
import com.careflow.patient.service.HealthRecordService;
import com.careflow.patient.service.PatientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthRecordControllerAuthorizationTest {
    // Authorization fixture only: production introspection is exercised by real HTTPS E2E tests.
    private final ActiveSessionClient sessions = mock(ActiveSessionClient.class);
    private final Map<String, ActiveSessionClient.Principal> activeSessions = new HashMap<>();

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID RECORD = UUID.randomUUID();
    private static final UUID DOCTOR = UUID.randomUUID();
    private static final String BASE = "/api/patients/" + PATIENT + "/health-records";
    private final PatientRepository patients = mock(PatientRepository.class);
    private final AssignmentClient assignments = mock(AssignmentClient.class);
    private final HealthRecordService records = mock(HealthRecordService.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final HealthRecordFileRepository files = mock(HealthRecordFileRepository.class);
    private final HealthRecordRepository recordRepository = mock(HealthRecordRepository.class);
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
        Patient patient = Patient.builder().userId(OWNER).fullName("Audit Patient").build();
        patient.setId(PATIENT);
        when(patients.findById(PATIENT)).thenReturn(Optional.of(patient));
        PatientService patientService = new PatientService(patients, assignments);
        HealthRecordController controller = new HealthRecordController(records, storage, files,
                recordRepository, new HealthRecordAccessService(patientService));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new ActiveSessionFilter(sessions, new ObjectMapper().findAndRegisterModules()),
                        new HealthRecordActorFilter(new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "DETAIL", "FILE", "POST", "PUT", "DELETE"})
    void everyOperationRejectsMissingTokenBeforeStorage(String operation) throws Exception {
        mvc.perform(operation(operation).header("X-User-Id", OWNER).header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(records, storage, files, recordRepository, assignments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "DETAIL", "FILE", "POST", "PUT", "DELETE"})
    void anotherPatientsTokenCannotReadOrMutateRecords(String operation) throws Exception {
        mvc.perform(operation(operation).header("Authorization", "Bearer " + token(UUID.randomUUID(), "PATIENT", true))
                        .header("X-User-Id", OWNER).header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(records, storage, files, recordRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "LAB_TECHNICIAN", "ADMIN"})
    void operationalAndAdminRolesCannotReadClinicalUploads(String role) throws Exception {
        mvc.perform(get(BASE).header("Authorization", "Bearer " + token(OWNER, role, true)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(records, storage, files, recordRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void doctorsCannotModifyPatientUploadedRecords(String operation) throws Exception {
        mvc.perform(operation(operation).header("Authorization", "Bearer " + token(DOCTOR, "DOCTOR", true)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(records, storage, files, recordRepository, assignments);
    }

    @Test
    void ownerCanReadAndChangeUploadedRecordUsingMobileContract() throws Exception {
        HealthRecordResponse record = HealthRecordResponse.builder().id(RECORD).patientId(PATIENT).build();
        when(records.getAll(eq(PATIENT), anyString())).thenReturn(List.of(record));
        when(records.getById(eq(PATIENT), eq(RECORD), anyString())).thenReturn(record);
        when(records.create(eq(PATIENT), any(), any(), anyString())).thenReturn(record);
        when(records.update(eq(PATIENT), eq(RECORD), any(), anyString())).thenReturn(record);
        String bearer = "Bearer " + token(OWNER, "PATIENT", true);
        mvc.perform(get(BASE).header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(RECORD.toString()));
        mvc.perform(operation("DETAIL").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(operation("POST").header("Authorization", bearer)).andExpect(status().isCreated());
        mvc.perform(operation("PUT").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(operation("DELETE").header("Authorization", bearer)).andExpect(status().isOk());
        verify(records).delete(PATIENT, RECORD);
    }

    @Test
    void assignedDoctorCanRead() throws Exception {
        when(assignments.getPatientAccess(eq(PATIENT), isNull(), isNull(), eq(DOCTOR), eq("DOCTOR")))
                .thenReturn(ApiResponse.success(AssignmentAccessResponse.allowed()));
        when(records.getAll(eq(PATIENT), anyString())).thenReturn(List.of());
        mvc.perform(get(BASE).header("Authorization", "Bearer " + token(DOCTOR, "DOCTOR", true)))
                .andExpect(status().isOk());
        verify(records).getAll(eq(PATIENT), anyString());
    }

    @Test
    void unavailableAssignmentDoesNotGrantDoctorAccess() throws Exception {
        when(assignments.getPatientAccess(eq(PATIENT), isNull(), isNull(), eq(DOCTOR), eq("DOCTOR")))
                .thenThrow(new IllegalStateException("Assignment dependency unavailable"));
        mvc.perform(get(BASE).header("Authorization", "Bearer " + token(DOCTOR, "DOCTOR", true)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(records, storage, files, recordRepository);
    }

    @Test
    void expiredJwtIsRejectedBeforeOwnershipLookup() throws Exception {
        mvc.perform(get(BASE).header("Authorization", "Bearer " + token(OWNER, "PATIENT", false)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(records, storage, files, recordRepository, assignments);
    }

    @Test
    void signedTokenWithoutExpirationIsRejected() throws Exception {
        String jwt = "authority-rejected-missing-expiration";
        mvc.perform(get(BASE).header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
        verifyNoInteractions(records, storage, files, recordRepository, assignments);
    }

    private MockHttpServletRequestBuilder operation(String operation) {
        if ("POST".equals(operation)) {
            return multipart(BASE).file(new MockMultipartFile("request", "request.json", "application/json",
                    "{\"title\":\"Audit\",\"recordDate\":\"2026-10-14\",\"facilityName\":\"Audit Hospital\"}".getBytes(StandardCharsets.UTF_8)));
        }
        String path = switch (operation) {
            case "DETAIL", "PUT", "DELETE" -> BASE + "/" + RECORD;
            case "FILE" -> BASE + "/files/" + UUID.randomUUID();
            default -> BASE;
        };
        HttpMethod method = switch (operation) {
            case "PUT" -> HttpMethod.PUT;
            case "DELETE" -> HttpMethod.DELETE;
            default -> HttpMethod.GET;
        };
        return request(method, path).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Updated\"}");
    }

    private String token(UUID userId, String role, boolean active) {
        String token = UUID.randomUUID().toString();
        activeSessions.put("Bearer " + token, new ActiveSessionClient.Principal(true, userId, role,
                UUID.randomUUID(), Instant.now().plusSeconds(active ? 60 : -60)));
        return token;
    }
}
