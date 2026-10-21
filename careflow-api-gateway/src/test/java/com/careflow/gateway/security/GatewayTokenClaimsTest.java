package com.careflow.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayTokenClaimsTest {
    private static final String SECRET = "gateway_claim_test_key_012345678901234567890123456789";

    @ParameterizedTest
    @ValueSource(strings = {"missing-subject", "missing-role", "missing-expiry", "missing-jti", "invalid-subject",
            "unknown-role", "wrong-issuer", "expired"})
    void invalidSignedClaimsReturn401WithoutForwarding(String kind) {
        var builder = Jwts.builder().id(UUID.randomUUID().toString()).subject(UUID.randomUUID().toString()).issuer("careflow-identity")
                .claim("role", "PATIENT").expiration(new Date(System.currentTimeMillis() + 60_000));
        switch (kind) {
            case "missing-jti" -> builder.id(null);
            case "missing-subject" -> builder.subject(null);
            case "missing-role" -> builder.claim("role", null);
            case "missing-expiry" -> builder.expiration(null);
            case "invalid-subject" -> builder.subject("not-a-uuid");
            case "unknown-role" -> builder.claim("role", "SUPERUSER");
            case "wrong-issuer" -> builder.issuer("untrusted");
            case "expired" -> builder.expiration(new Date(System.currentTimeMillis() - 60_000));
        }
        String token = builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        var filter = new JwtAuthenticationFilter(new ObjectMapper(), GatewaySessionTestSupport.activeSession(SECRET));
        var exchange = org.springframework.mock.web.server.MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());
        filter.filter(exchange, ignored -> { throw new AssertionError("Invalid claims must not be forwarded"); }).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
