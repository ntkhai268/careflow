package com.careflow.appointment.client;
import com.careflow.appointment.service.AppointmentPricingFixture;

import com.careflow.appointment.config.AppointmentSecurityConfiguration;
import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.appointment.service.AppointmentEventService;
import com.careflow.appointment.service.AppointmentService;
import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientBookingClientContractTest {
    private static final UUID PATIENT = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private HttpServer server;
    private PatientClient client;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> role = new AtomicReference<>();
    private byte[] payload;
    private int providerStatus;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/patients/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            role.set(exchange.getRequestHeaders().getFirst("X-User-Role"));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(providerStatus, payload.length);
            try (var output = exchange.getResponseBody()) { output.write(payload); }
        });
        server.start();
        var mapper = new ObjectMapper().findAndRegisterModules();
        client = Feign.builder().contract(new SpringMvcContract())
                .requestInterceptor(new AppointmentSecurityConfiguration().authenticatedFeignRequests())
                .decoder((response, type) -> mapper.readValue(response.body().asInputStream(), mapper.constructType(type)))
                .target(PatientClient.class, "http://127.0.0.1:" + server.getAddress().getPort() + "/api/patients");
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer original-contract-token");
        request.addHeader("X-User-Id", OWNER.toString());
        request.addHeader("X-User-Role", "PATIENT");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        RequestContextHolder.resetRequestAttributes();
    }

    private void fixture(int status) throws Exception {
        providerStatus = status;
        try (var input = new ClassPathResource("contracts/patient/booking-" + (status == 200 ? "success" : "error-" + status) + ".json").getInputStream()) {
            payload = input.readAllBytes();
        }
    }

    @Test
    void actualFeignContractUsesSpecificPatientPathAndOriginalCredential() throws Exception {
        fixture(200);
        var response = client.getBookingContext(PATIENT);
        assertThat(path.get()).isEqualTo("/api/patients/" + PATIENT + "/booking-context");
        assertThat(authorization.get()).isEqualTo("Bearer original-contract-token");
        assertThat(role.get()).isEqualTo("PATIENT");
        assertThat(response.getData().id()).isEqualTo(PATIENT);
        assertThat(response.getData().userId()).isEqualTo(OWNER);
        assertThat(response.getData().fullName()).isEqualTo("Verified synthetic profile");
    }

    @ParameterizedTest
    @CsvSource({"401,401", "403,403", "404,404", "500,503", "503,503"})
    void errorFixturesDenyBookingBeforeAnyMutation(int remote, int expected) throws Exception {
        fixture(remote);
        var repository = mock(AppointmentRepository.class);
        var events = mock(AppointmentEventService.class);
        var service = new AppointmentService(repository, events, AppointmentPricingFixture.priced(null), client, null,
                mock(AppointmentConsultationClient.class));
        var request = CreateAppointmentRequest.builder().patientId(PATIENT).build();
        assertThatThrownBy(() -> service.bookAppointment(request, OWNER, "PATIENT", "trace", "key"))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus()).isEqualTo(expected));
        verifyNoInteractions(repository, events);
    }
}
