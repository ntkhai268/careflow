package com.careflow.prescription.controller;

import java.util.HashMap;
import java.util.Map;
import java.time.Instant;
import com.careflow.common.security.ActiveSessionFilter;
import com.careflow.common.security.ActiveSessionClient;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.GlobalExceptionHandler;
import com.careflow.prescription.dto.response.PrescriptionResponse;
import com.careflow.prescription.service.PrescriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrescriptionAuthenticationRegressionTest {
    // Authorization fixture only: production introspection is exercised by real HTTPS E2E tests.
    private final ActiveSessionClient sessions = mock(ActiveSessionClient.class);
    private final Map<String, ActiveSessionClient.Principal> activeSessions = new HashMap<>();

    private static final UUID ID = UUID.randomUUID();
    private static final UUID DOCTOR = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private PrescriptionService service;
    private ActiveSessionFilter filter;
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
        service = mock(PrescriptionService.class);
        filter = new ActiveSessionFilter(sessions, new ObjectMapper().findAndRegisterModules());
        mvc = MockMvcBuilders.standaloneSetup(new PrescriptionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(filter).build();
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/prescriptions/medicines", "GET,/api/prescriptions/patient/", 
            "GET,/api/prescriptions/consultation/", "GET,/api/prescriptions/", 
            "POST,/api/prescriptions", "PUT,/api/prescriptions/", 
            "POST,/api/prescriptions/ID/confirm", "PUT,/api/prescriptions/ID/confirm",
            "POST,/api/prescriptions/ID/cancel", "POST,/api/prescriptions/ID/dispense",
            "POST,/api/prescriptions/ID/amendments"})
    void everyPrescriptionApiRejectsMissingTokenDespiteSpoofedHeaders(String method, String path) throws Exception {
        path = path.replace("ID", ID.toString());
        if (path.endsWith("/")) path += ID;
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path)
                        .header("X-User-Id", DOCTOR).header("X-User-Role", "DOCTOR")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "wrong-issuer", "missing-expiry", "missing-role",
            "unknown-role", "invalid-subject", "missing-subject", "wrong-key", "malformed"})
    void invalidTokensNeverReachPrescriptionService(String kind) throws Exception {
        // The authority rejects each invalid-token category; the controller must never execute.
        String token = "rejected-" + kind;
        mvc.perform(post("/api/prescriptions/" + ID + "/confirm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void bothConfirmationAliasesUseSignedDoctorIdentity(String method) throws Exception {
        when(service.confirmPrescription(ID, DOCTOR, "DOCTOR")).thenReturn(new PrescriptionResponse());
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/api/prescriptions/" + ID + "/confirm")
                        .header("Authorization", "Bearer " + token(DOCTOR, "DOCTOR"))
                        .header("X-User-Id", PATIENT).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
        verify(service).confirmPrescription(ID, DOCTOR, "DOCTOR");
        verify(service, never()).confirmPrescription(ID);
    }

    @Test
    void patientCannotImpersonateDoctorWithHeaders() throws Exception {
        when(service.confirmPrescription(ID, PATIENT, "PATIENT"))
                .thenThrow(new BusinessException(403, "Assigned doctor access is required"));
        mvc.perform(post("/api/prescriptions/" + ID + "/confirm")
                        .header("Authorization", "Bearer " + token(PATIENT, "PATIENT"))
                        .header("X-User-Id", DOCTOR).header("X-User-Role", "DOCTOR"))
                .andExpect(status().isForbidden());
        verify(service).confirmPrescription(ID, PATIENT, "PATIENT");
        verify(service, never()).confirmPrescription(ID);
    }

    @Test
    void controllerDoesNotFallBackToStoredDoctorWhenActorMissing() throws Exception {
        MockMvc withoutFilter = MockMvcBuilders.standaloneSetup(new PrescriptionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.confirmPrescription(ID, null, null)).thenThrow(new BusinessException(403, "Actor required"));
        withoutFilter.perform(post("/api/prescriptions/" + ID + "/confirm")).andExpect(status().isForbidden());
        verify(service).confirmPrescription(ID, null, null);
        verify(service, never()).confirmPrescription(ID);
    }

    @Test
    void optionsAndInfrastructurePathsRemainOutsidePrescriptionFilter() throws Exception {
        for (String path : new String[]{"/api/prescriptions/medicines"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(path);
            request.setMethod(path.startsWith("/actuator") ? "GET" : "OPTIONS");
            AtomicBoolean invoked = new AtomicBoolean();
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> invoked.set(true));
            assertThat(invoked).isTrue();
        }
    }

    private String token(UUID userId, String role) {
        String token = UUID.randomUUID().toString();
        activeSessions.put("Bearer " + token, new ActiveSessionClient.Principal(true, userId, role,
                UUID.randomUUID(), Instant.now().plusSeconds(60)));
        return token;
    }
}
