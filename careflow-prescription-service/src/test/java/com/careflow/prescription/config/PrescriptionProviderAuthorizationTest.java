package com.careflow.prescription.config;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PrescriptionProviderAuthorizationTest {
    private static final UUID CONSULTATION = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    private static final UUID DOCTOR = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private ConsultationClient consultations;
    private PatientIdentityClient patients;
    private MockRestServiceServer consultationServer;
    private MockRestServiceServer patientServer;

    @BeforeEach
    void setUp() {
        consultations = new ConsultationClient(new RestTemplateBuilder());
        patients = new PatientIdentityClient(new RestTemplateBuilder());
        ReflectionTestUtils.setField(consultations, "consultationServiceUrl", "http://consultation.test");
        ReflectionTestUtils.setField(patients, "patientServiceUrl", "http://patient.test");
        consultationServer = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(consultations, "restTemplate")).build();
        patientServer = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(patients, "restTemplate")).build();
        context("PATIENT", USER);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        consultationServer.verify();
        patientServer.verify();
    }

    @Test
    void consultationQueryForwardsActualBearerAndCorrelationAndReadsBinding() throws Exception {
        context("DOCTOR", DOCTOR);
        consultationServer.expect(requestTo("http://consultation.test/api/consultations/" + CONSULTATION))
                .andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer signed-fixture-token"))
                .andExpect(header("X-Correlation-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(header("X-User-Id", DOCTOR.toString())).andExpect(header("X-User-Role", "DOCTOR"))
                .andRespond(withSuccess(fixture("consultation", "success"), MediaType.APPLICATION_JSON));
        var context = consultations.getConsultation(CONSULTATION);
        assertThat(context.patientId()).isEqualTo(PATIENT);
        assertThat(context.doctorId()).isEqualTo(DOCTOR);
        assertThat(context.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void patientQueryUsesRequestedProfileAndOriginalToken() throws Exception {
        patientServer.expect(requestTo("http://patient.test/api/patients/" + PATIENT))
                .andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer signed-fixture-token"))
                .andExpect(header("X-User-Id", USER.toString())).andExpect(header("X-User-Role", "PATIENT"))
                .andRespond(withSuccess(fixture("patient", "success"), MediaType.APPLICATION_JSON));
        patients.requirePatientAccess(PATIENT, USER, "PATIENT");
    }

    @ParameterizedTest
    @CsvSource({"401,401", "403,403", "404,404", "500,503", "503,503"})
    void consultationErrorsRemainDeniedOrUnavailable(int remote, int expected) throws Exception {
        consultationServer.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(remote))
                .contentType(MediaType.APPLICATION_JSON).body(fixture("consultation", "error-" + remote)));
        assertStatus(expected, () -> consultations.getConsultation(CONSULTATION));
    }

    @ParameterizedTest
    @CsvSource({"401,401", "403,403", "404,404", "500,503", "503,503"})
    void patientErrorsRemainDeniedOrUnavailable(int remote, int expected) throws Exception {
        patientServer.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(remote))
                .contentType(MediaType.APPLICATION_JSON).body(fixture("patient", "error-" + remote)));
        assertStatus(expected, () -> patients.requirePatientAccess(PATIENT, USER, "PATIENT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"data", "id", "patientId", "doctorId", "status"})
    void incompleteConsultationNeverFallsBackToSuccess(String missing) throws Exception {
        var body = new ObjectMapper().readTree(fixture("consultation", "success"));
        if (missing.equals("data")) ((com.fasterxml.jackson.databind.node.ObjectNode) body).remove("data");
        else ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("data")).remove(missing);
        consultationServer.expect(anything()).andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));
        assertStatus(503, () -> consultations.getConsultation(CONSULTATION));
    }

    @Test
    void incorrectConsultationIdIsUnavailable() throws Exception {
        consultationServer.expect(anything()).andRespond(withSuccess(fixture("consultation", "success"), MediaType.APPLICATION_JSON));
        assertStatus(503, () -> consultations.getConsultation(UUID.randomUUID()));
    }

    @Test
    void wrongOwnerCannotBeAuthorizedEvenIfProviderReturnsProfile() throws Exception {
        patientServer.expect(anything()).andRespond(withSuccess(fixture("patient", "success"), MediaType.APPLICATION_JSON));
        assertStatus(403, () -> patients.requirePatientAccess(PATIENT, UUID.randomUUID(), "PATIENT"));
    }

    @Test
    void wrongProfileIdCannotBeMistakenForRequestedProfile() throws Exception {
        patientServer.expect(anything()).andRespond(withSuccess(fixture("patient", "success"), MediaType.APPLICATION_JSON));
        assertStatus(503, () -> patients.requirePatientAccess(UUID.randomUUID(), USER, "PATIENT"));
    }

    @Test
    void networkFailuresCannotBecomeAnEmptyOrAuthorizedResponse() {
        consultationServer.expect(anything()).andRespond(withException(new IOException("Synthetic timeout")));
        patientServer.expect(anything()).andRespond(withException(new IOException("Synthetic timeout")));
        assertStatus(503, () -> consultations.getConsultation(CONSULTATION));
        assertStatus(503, () -> patients.requirePatientAccess(PATIENT, USER, "PATIENT"));
    }

    @Test
    void missingRequestContextNeverCreatesFallbackIdentity() {
        RequestContextHolder.resetRequestAttributes();
        assertStatus(401, () -> consultations.getConsultation(CONSULTATION));
        assertStatus(401, () -> patients.requirePatientAccess(PATIENT, USER, "PATIENT"));
    }

    @Test
    void missingBearerNeverMakesAnAnonymousProviderCall() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        assertStatus(401, () -> consultations.getConsultation(CONSULTATION));
        assertStatus(401, () -> patients.requirePatientAccess(PATIENT, USER, "PATIENT"));
    }

    private void context(String role, UUID actor) {
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer signed-fixture-token");
        request.addHeader("X-User-Id", actor.toString());
        request.addHeader("X-User-Role", role);
        request.addHeader("X-Correlation-Id", "11111111-1111-1111-1111-111111111111");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private String fixture(String service, String suffix) throws IOException {
        return new ClassPathResource("contracts/" + service + "/access-" + suffix + ".json")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private void assertStatus(int status, Runnable command) {
        assertThatThrownBy(command::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(status));
    }
}
