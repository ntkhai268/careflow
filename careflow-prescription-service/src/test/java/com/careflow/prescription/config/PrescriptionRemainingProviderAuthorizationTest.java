package com.careflow.prescription.config;

import com.careflow.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PrescriptionRemainingProviderAuthorizationTest {
    private final UUID actor = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final UUID consultation = UUID.randomUUID();
    private final UUID prescription = UUID.randomUUID();
    private AppointmentClient appointments;
    private QueueExecutionClient queues;
    private MockRestServiceServer appointmentServer;
    private MockRestServiceServer queueServer;

    @BeforeEach
    void setUp() {
        appointments = new AppointmentClient(new RestTemplateBuilder());
        queues = new QueueExecutionClient(new RestTemplateBuilder());
        ReflectionTestUtils.setField(appointments, "appointmentServiceUrl", "http://appointment.test");
        ReflectionTestUtils.setField(queues, "queueServiceUrl", "http://queue.test");
        appointmentServer = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(appointments, "restTemplate")).build();
        queueServer = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(queues, "restTemplate")).build();
    }

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void context(String role) {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer actual-signed-token");
        request.addHeader("X-User-Id", actor.toString());
        request.addHeader("X-User-Role", role);
        request.addHeader("X-Correlation-Id", "11111111-1111-1111-1111-111111111111");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void followUpPropagatesOriginalBearerAndIdempotencyKey() {
        context("DOCTOR");
        LocalDate date = LocalDate.now().plusDays(7);
        appointmentServer.expect(requestTo("http://appointment.test/api/appointments/follow-ups"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer actual-signed-token"))
                .andExpect(header("X-User-Id", actor.toString())).andExpect(header("X-User-Role", "DOCTOR"))
                .andExpect(header("X-Correlation-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(header("Idempotency-Key", "follow-up-" + consultation + "-" + date))
                .andExpect(content().json("{\"department\":\"CO_XUONG_KHOP\",\"timeSlot\":\"14:00-14:30\"}",false))
                .andRespond(withSuccess("{\"status\":201,\"data\":{\"id\":\""+prescription+"\",\"patientId\":\""+patient+"\",\"sourceConsultationId\":\""+consultation+"\",\"status\":\"CONFIRMED\"}}", MediaType.APPLICATION_JSON));
        assertThat(appointments.schedule(patient,consultation,date,"CO_XUONG_KHOP","14:00-14:30",null,actor)).isEqualTo(prescription);
        appointmentServer.verify();
    }

    @Test
    void followUpCannotUseDifferentActorArgument() {
        context("DOCTOR");
        assertStatus(403, () -> appointments.createFollowUpAppointment(patient, actor, consultation,
                LocalDate.now().plusDays(7), null, UUID.randomUUID()));
    }

    @Test
    void missingContextNeverCreatesDoctorIdentity() {
        assertStatus(401, () -> appointments.createFollowUpAppointment(patient, actor, consultation,
                LocalDate.now().plusDays(7), null, actor));
        assertStatus(401, () -> queues.getPharmacyEntry(prescription, actor, "STAFF"));
    }

    @Test
    void queueReadForwardsBearerAndVerifiedStaff() {
        context("STAFF");
        queueServer.expect(requestTo("http://queue.test/api/queues/prescriptions/" + prescription + "/current"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer actual-signed-token"))
                .andExpect(header("X-User-Id", actor.toString())).andExpect(header("X-User-Role", "STAFF"))
                .andRespond(withSuccess("{\"status\":200,\"data\":{\"prescriptionId\":\"" + prescription + "\"}}", MediaType.APPLICATION_JSON));
        assertThat(queues.getPharmacyEntry(prescription, actor, "STAFF").getPrescriptionId()).isEqualTo(prescription);
        queueServer.verify();
    }

    @Test
    void queueCompletionForwardsOriginalBearer() {
        context("STAFF");
        queueServer.expect(requestTo("http://queue.test/api/queues/prescriptions/" + prescription + "/complete"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer actual-signed-token"))
                .andExpect(header("X-User-Id", actor.toString())).andExpect(header("X-User-Role", "STAFF"))
                .andRespond(withSuccess());
        queues.completePharmacyEntry(prescription, actor, "STAFF", null);
        queueServer.verify();
    }

    @Test
    void queueCannotUseDifferentActorArgument() {
        context("STAFF");
        assertStatus(403, () -> queues.getPharmacyEntry(prescription, UUID.randomUUID(), "STAFF"));
    }

    @Test
    void queueCannotPromoteStaffByRoleArgument() {
        context("STAFF");
        assertStatus(403, () -> queues.getPharmacyEntry(prescription, actor, "ADMIN"));
    }

    private void assertStatus(int status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(status));
    }
}
