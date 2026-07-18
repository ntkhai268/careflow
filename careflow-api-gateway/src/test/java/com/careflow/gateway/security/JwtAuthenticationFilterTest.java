package com.careflow.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
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

class JwtAuthenticationFilterTest {
    private static final String SECRET = "a-very-long-test-secret-that-is-at-least-thirty-two-bytes";

    @Test
    void replacesSpoofedTrustedHeadersWithSignedClaims() {
        UUID userId = UUID.randomUUID();
        String token = Jwts.builder().subject(userId.toString()).issuer("careflow-identity")
                .claim("role", "PATIENT").issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET, "careflow-identity", new ObjectMapper());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/queues/me/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "ADMIN").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(forwardedExchange);
            return forwardedExchange.getResponse().setComplete();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo(userId.toString());
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("PATIENT");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void protectedRequestWithoutTokenIsUnauthorized() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET, "careflow-identity", new ObjectMapper());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/queues/me/status").build());

        filter.filter(exchange, ignored -> { throw new AssertionError("Request must not be forwarded"); }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
