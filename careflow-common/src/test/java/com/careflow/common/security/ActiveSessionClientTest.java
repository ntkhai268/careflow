package com.careflow.common.security;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class ActiveSessionClientTest {
    private HttpServer server;
    private ActiveSessionClient client;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private int responseStatus;
    private String responseBody;
    private AtomicReference<String> forwardedAuthorization;
    private AtomicReference<String> forwardedCorrelation;

    @BeforeEach
    void setUp() throws Exception {
        responseStatus = 200;
        responseBody = success();
        forwardedAuthorization = new AtomicReference<>();
        forwardedCorrelation = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/session", exchange -> {
            forwardedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            forwardedCorrelation.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new ActiveSessionClient("http://127.0.0.1:" + server.getAddress().getPort(), mapper, Duration.ofSeconds(1));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void returnsOnlyIdentityValidatedPrincipalAndForwardsOriginalBearer() {
        var principal = client.verify("Bearer fixture-token", "11111111-1111-1111-1111-111111111111");
        assertThat(principal.role()).isEqualTo("PATIENT");
        assertThat(forwardedAuthorization.get()).isEqualTo("Bearer fixture-token");
        assertThat(forwardedCorrelation.get()).isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @ParameterizedTest
    @CsvSource({"401,401", "403,403", "404,503", "500,503", "503,503", "302,503"})
    void authorityErrorsNeverAuthorize(int remote, int expected) {
        responseStatus = remote;
        responseBody = "{}";
        denied(expected, () -> client.verify("Bearer fixture-token", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "userId", "role", "sessionId", "expiresAt", "data", "status", "invalid-json"})
    void incompleteAuthorityResponseIsUnavailable(String missing) throws Exception {
        if (missing.equals("invalid-json")) responseBody = "invalid";
        else {
            var body = mapper.readTree(responseBody);
            if (missing.equals("data") || missing.equals("status")) ((com.fasterxml.jackson.databind.node.ObjectNode) body).remove(missing);
            else ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("data")).remove(missing);
            responseBody = body.toString();
        }
        denied(503, () -> client.verify("Bearer fixture-token", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "expired"})
    void inactivePrincipalIsUnauthorized(String reason) throws Exception {
        var body = mapper.readTree(responseBody);
        var data = (com.fasterxml.jackson.databind.node.ObjectNode) body.get("data");
        if (reason.equals("inactive")) data.put("active", false);
        else data.put("expiresAt", Instant.now().minusSeconds(1).toString());
        responseBody = body.toString();
        denied(401, () -> client.verify("Bearer fixture-token", null));
    }

    @Test
    void stoppedAuthorityIsUnavailableWithoutFallback() {
        server.stop(0);
        denied(503, () -> client.verify("Bearer fixture-token", null));
    }

    @Test
    void missingBearerDoesNotCallAuthority() {
        denied(401, () -> client.verify(null, null));
        assertThat(forwardedAuthorization.get()).isNull();
    }

    private String success() throws Exception {
        return mapper.writeValueAsString(Map.of("status", 200, "data", Map.of("active", true,
                "userId", UUID.randomUUID(), "role", "PATIENT", "sessionId", UUID.randomUUID(),
                "expiresAt", Instant.now().plusSeconds(60))));
    }

    private void denied(int status, Runnable command) {
        assertThatThrownBy(command::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(status));
    }
}
