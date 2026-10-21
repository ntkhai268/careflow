package com.careflow.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryAuthenticationRegressionTest {
    private static final String SECRET = "a-very-long-test-secret-that-is-at-least-thirty-two-bytes";
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            new ObjectMapper(), GatewaySessionTestSupport.activeSession(SECRET));

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/directory/staff/assignments",
            "/api/directory/staff/55555555-5555-5555-5555-555555555555/room-access",
            "/api/directory/doctors/me",
            "/api/directory/doctors/d0000001-0000-0000-0000-000000000001",
            "/api/directory/departments/NOI_TONG_QUAT/internal",
            "/api/directory/rooms/ROOM-01",
            "/api/directory/future-protected-resource"
    })
    void protectedDirectoryReadsRequireToken(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header("X-User-Role", "ADMIN").build());

        filter.filter(exchange, ignored -> { throw new AssertionError("must not forward"); }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/directory/departments",
            "/api/directory/departments/NOI_TONG_QUAT",
            "/api/directory/rooms",
            "/api/directory/doctors"
    })
    void publicCatalogReadsStillStripSpoofedIdentity(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "ADMIN").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, request -> {
            forwarded.set(request);
            return request.getResponse().setComplete();
        }).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders()).doesNotContainKeys("X-User-Id", "X-User-Role");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATIENT", "DOCTOR", "STAFF", "LAB_TECHNICIAN", "ADMIN"})
    void protectedDirectoryReadsForwardSignedIdentity(String role) {
        UUID userId = UUID.randomUUID();
        String token = Jwts.builder().id(UUID.randomUUID().toString()).subject(userId.toString()).issuer("careflow-identity")
                .claim("role", role).issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/directory/staff/assignments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "ADMIN").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, request -> {
            forwarded.set(request);
            return request.getResponse().setComplete();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo(userId.toString());
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo(role);
    }

    @Test
    void writingPublicCatalogStillRequiresToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/directory/departments").build());

        filter.filter(exchange, ignored -> { throw new AssertionError("must not forward"); }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
