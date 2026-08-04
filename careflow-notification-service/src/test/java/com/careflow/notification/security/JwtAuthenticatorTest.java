package com.careflow.notification.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtAuthenticatorTest {
    private static final String SECRET = "careflow-test-jwt-secret-key-must-be-at-least-256-bits-long";

    @Test
    void authenticatesSupportedClinicalRole() {
        UUID userId = UUID.randomUUID();
        String token = token(userId, "LAB_TECHNICIAN", "careflow-identity");

        var authentication = new JwtAuthenticator(SECRET, "careflow-identity")
                .authenticate("Bearer " + token);

        assertThat(authentication.getName()).isEqualTo(userId.toString());
        assertThat(authentication.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_LAB_TECHNICIAN");
    }

    @Test
    void rejectsWrongIssuer() {
        String token = token(UUID.randomUUID(), "PATIENT", "another-issuer");
        JwtAuthenticator authenticator = new JwtAuthenticator(SECRET, "careflow-identity");

        assertThatThrownBy(() -> authenticator.authenticate("Bearer " + token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsMissingBearerToken() {
        assertThatThrownBy(() -> new JwtAuthenticator(SECRET, "careflow-identity").authenticate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bearer");
    }

    private String token(UUID userId, String role, String issuer) {
        Instant now = Instant.now();
        return Jwts.builder().subject(userId.toString()).issuer(issuer).claim("role", role)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
