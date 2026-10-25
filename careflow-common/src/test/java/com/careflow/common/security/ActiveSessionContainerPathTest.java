package com.careflow.common.security;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActiveSessionContainerPathTest {
    private static final UUID OWNER = UUID.fromString("a3000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("a3000000-0000-0000-0000-000000000002");
    private static ServletWebServerApplicationContext context;
    private static String base;
    private static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void startContainer() {
        var application = new SpringApplication(FixtureApplication.class);
        application.setDefaultProperties(Map.of("server.port", 0, "server.address", "127.0.0.1",
                "spring.main.banner-mode", "off", "logging.level.root", "ERROR"));
        context = (ServletWebServerApplicationContext) application.run();
        base = "http://127.0.0.1:" + context.getWebServer().getPort();
    }

    @AfterAll
    static void stopContainer() {
        if (context != null) context.close();
    }

    private HttpResponse<String> request(String prefix, String token, UUID forgedUser) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + prefix + OWNER)).timeout(Duration.ofSeconds(5))
                .header("X-User-Id", forgedUser.toString()).header("X-User-Role", "ADMIN")
                .header("Content-Type", "application/json;charset=UTF-16");
        if (token != null) request.header("Authorization", token);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/patients/", "/api;/patients/", "/api;v=1/patients/", "/%61pi/patients/",
            "/a%70i/patients/", "/%61%70%69/patients/", "/api/patients;v=1/"})
    void missingTokenCannotReachControllerThroughEquivalentContainerPaths(String prefix) throws Exception {
        int before = context.getBean(ApiFixture.class).calls.get();
        assertThat(request(prefix, null, OWNER).statusCode()).isEqualTo(401);
        assertThat(context.getBean(ApiFixture.class).calls.get()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api;/patients/", "/%61pi/patients/", "/api/patients;v=1/"})
    void validOtherUserCannotSpoofOwnerThroughPathVariants(String prefix) throws Exception {
        assertThat(request(prefix, "Bearer other", OWNER).statusCode()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/patients/", "/api;/patients/", "/%61pi/patients/"})
    void authenticatedOwnerAlwaysUsesVerifiedIdentity(String prefix) throws Exception {
        var response = request(prefix, "Bearer owner", OTHER);
        assertThat(response.statusCode()).isEqualTo(200);
        var json = new ObjectMapper().readTree(response.body());
        assertThat(json.get("actor").asText()).isEqualTo(OWNER.toString());
        assertThat(json.get("role").asText()).isEqualTo("PATIENT");
    }

    @Test
    void infrastructureRemainsOutsideApiFilterMapping() throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    @Import(GlobalExceptionHandler.class)
    static class FixtureApplication {
        @Bean
        ActiveSessionClient authority() {
            var client = mock(ActiveSessionClient.class);
            when(client.verifyRequest(nullable(String.class), anyString(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
                String token = invocation.getArgument(0);
                if ("Bearer owner".equals(token) || "Bearer other".equals(token)) {
                    return new ActiveSessionClient.Principal(true, "Bearer owner".equals(token) ? OWNER : OTHER,
                            "PATIENT", UUID.randomUUID(), Instant.now().plusSeconds(60));
                }
                throw new BusinessException(401, "Missing or invalid session");
            });
            return client;
        }

        @Bean
        ApiFixture apiFixture() { return new ApiFixture(); }
    }

    @RestController
    static class ApiFixture {
        final AtomicInteger calls = new AtomicInteger();

        @GetMapping("/api/patients/{id}")
        public Map<String, String> profile(@PathVariable("id") UUID id, @RequestHeader("X-User-Id") UUID actor,
                                           @RequestHeader("X-User-Role") String role) {
            calls.incrementAndGet();
            if (!id.equals(actor)) throw new BusinessException(403, "Other patient");
            return Map.of("actor", actor.toString(), "role", role);
        }

        @GetMapping("/actuator/health")
        public Map<String, String> health() { return Map.of("status", "UP"); }
    }
}
