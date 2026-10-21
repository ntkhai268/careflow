package com.careflow.consultation.controller;

import java.util.HashMap;
import java.util.Map;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.ActiveSessionFilter;
import com.careflow.common.security.ActiveSessionClient;
import com.careflow.common.exception.GlobalExceptionHandler;
import com.careflow.consultation.client.AppointmentClient;
import com.careflow.consultation.client.DirectoryClient;
import com.careflow.consultation.mapper.ConsultationMapper;
import com.careflow.consultation.model.Consultation;
import com.careflow.consultation.model.ConsultationStatus;
import com.careflow.consultation.repository.ConsultationRepository;
import com.careflow.consultation.service.ConsultationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConsultationAuthorizationRegressionTest {
    // Authorization fixture only: production introspection is exercised by real HTTPS E2E tests.
    private final ActiveSessionClient sessions = mock(ActiveSessionClient.class);
    private final Map<String, ActiveSessionClient.Principal> activeSessions = new HashMap<>();

    private static final UUID ID = UUID.randomUUID();
    private static final UUID DOCTOR = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private final ConsultationRepository repository = mock(ConsultationRepository.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final AppointmentClient appointments = mock(AppointmentClient.class);
    private final DirectoryClient directory = mock(DirectoryClient.class);
    private Consultation consultation;
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
        consultation = Consultation.builder().patientId(PATIENT).doctorId(DOCTOR)
                .status(ConsultationStatus.IN_PROGRESS).diagnosis("Original diagnosis").build();
        consultation.setId(ID);
        when(repository.findById(ID)).thenReturn(Optional.of(consultation));
        ConsultationService service = new ConsultationService(repository, new ConsultationMapper(), rabbit,
                appointments, mock(com.careflow.consultation.client.ConsultationQueueClient.class), directory, mock(com.careflow.consultation.handover.DoctorWorkloadLock.class), mock(com.careflow.consultation.client.ConsultationLabClient.class));
        mvc = MockMvcBuilders.standaloneSetup(new ConsultationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new ActiveSessionFilter(sessions, new ObjectMapper().findAndRegisterModules())).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "READ", "STATUS", "UPDATE", "CLINICAL", "STATE", "COMPLETE_POST", "COMPLETE_PUT"})
    void spoofedHeadersWithoutJwtNeverReachBusinessLogic(String operation) throws Exception {
        mvc.perform(operation(operation).header("X-User-Id", DOCTOR).header("X-User-Role", "DOCTOR"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, rabbit, appointments, mock(com.careflow.consultation.client.ConsultationQueueClient.class), directory, mock(com.careflow.consultation.handover.DoctorWorkloadLock.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE", "CLINICAL", "STATE", "COMPLETE_POST", "COMPLETE_PUT"})
    void patientCannotUseLegacyOrModernClinicalCommands(String operation) throws Exception {
        mvc.perform(operation(operation).header("Authorization", "Bearer " + token(UUID.randomUUID(), "PATIENT"))
                        .header("X-User-Id", DOCTOR).header("X-User-Role", "DOCTOR"))
                .andExpect(status().isForbidden());
        verify(repository, never()).save(any());
        verifyNoInteractions(rabbit, appointments, mock(com.careflow.consultation.client.ConsultationQueueClient.class), directory, mock(com.careflow.consultation.handover.DoctorWorkloadLock.class));
        assertThat(consultation.getDiagnosis()).isEqualTo("Original diagnosis");
        assertThat(consultation.getStatus()).isEqualTo(ConsultationStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE", "CLINICAL", "STATE", "COMPLETE_POST", "COMPLETE_PUT"})
    void anotherDoctorCannotChangeTheConsultation(String operation) throws Exception {
        mvc.perform(operation(operation).header("Authorization", "Bearer " + token(UUID.randomUUID(), "DOCTOR")))
                .andExpect(status().isForbidden());
        verify(repository, never()).save(any());
        verifyNoInteractions(rabbit, appointments, mock(com.careflow.consultation.client.ConsultationQueueClient.class), directory, mock(com.careflow.consultation.handover.DoctorWorkloadLock.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE", "CLINICAL"})
    void assignedDoctorCanSaveViaBothRoutes(String operation) throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(operation(operation).header("Authorization", "Bearer " + token(DOCTOR, "DOCTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.diagnosis").value("Authorized update"));
        verify(repository).save(consultation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STATE", "COMPLETE_POST", "COMPLETE_PUT"})
    void noCompletionAliasCanBypassPendingResults(String operation) throws Exception {
        consultation.setStatus(ConsultationStatus.AWAITING_CLS);
        mvc.perform(operation(operation).header("Authorization", "Bearer " + token(DOCTOR, "DOCTOR")))
                .andExpect(status().isConflict());
        verify(repository, never()).save(any());
        verifyNoInteractions(rabbit, appointments, mock(com.careflow.consultation.client.ConsultationQueueClient.class), directory, mock(com.careflow.consultation.handover.DoctorWorkloadLock.class));
    }

    @Test
    void serverControlledReviewStatusCannotBeSetThroughLegacyRoute() throws Exception {
        mvc.perform(put("/api/consultations/" + ID + "/status")
                        .header("Authorization", "Bearer " + token(DOCTOR, "DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AWAITING_REVIEW\"}"))
                .andExpect(status().isConflict());
        verify(repository, never()).save(any());
    }

    @Test
    void assignedDoctorCanReadStatus() throws Exception {
        mvc.perform(operation("STATUS").header("Authorization", "Bearer " + token(DOCTOR, "DOCTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("IN_PROGRESS"));
    }

    @Test
    void anotherDoctorCannotReadStatus() throws Exception {
        mvc.perform(operation("STATUS").header("Authorization", "Bearer " + token(UUID.randomUUID(), "DOCTOR")))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder operation(String operation) {
        String base = "/api/consultations/" + ID;
        return switch (operation) {
            case "CREATE" -> post("/api/consultations").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"appointmentId\":\"" + UUID.randomUUID() + "\",\"patientId\":\"" + PATIENT
                            + "\",\"doctorId\":\"" + DOCTOR + "\"}");
            case "READ" -> get(base);
            case "STATUS" -> get(base + "/status");
            case "UPDATE", "CLINICAL" -> put(base + (operation.equals("CLINICAL") ? "/clinical-data" : ""))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"diagnosis\":\"Authorized update\"}");
            case "STATE" -> put(base + "/status").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"COMPLETED\"}");
            case "COMPLETE_POST" -> post(base + "/complete");
            default -> request(HttpMethod.PUT, base + "/complete");
        };
    }

    private String token(UUID userId, String role) {
        String token = UUID.randomUUID().toString();
        activeSessions.put("Bearer " + token, new ActiveSessionClient.Principal(true, userId, role,
                UUID.randomUUID(), Instant.now().plusSeconds(60)));
        return token;
    }
}
