package com.careflow.identity.security;

import com.careflow.identity.domain.*;
import com.careflow.identity.repository.RefreshTokenRepository;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtSessionValidationTest {
    private static final String SECRET = "session_test_key_0123456789012345678901234567890123456789";
    private RefreshTokenRepository repository;
    private JwtService service;
    private User user;
    private RefreshToken session;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        service = new JwtService(SigningTestKeys.keys(), 3_600_000, "careflow-identity", "careflow-api", repository);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(UserRole.DOCTOR);
        user.setStatus(UserStatus.ACTIVE);
        session = new RefreshToken();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setDpopJkt("test-client-key-thumbprint");
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));
    }

    @ParameterizedTest
    @ValueSource(strings = {"revoked", "expired", "unknown", "wrong-owner", "disabled", "locked", "changed-role"})
    void inactiveAuthorityRejectsStillCryptographicallyValidToken(String reason) {
        String token = service.issue(user, session.getId());
        switch (reason) {
            case "revoked" -> session.revoke(Instant.now(), null);
            case "expired" -> session.setExpiresAt(Instant.now().minusSeconds(1));
            case "unknown" -> when(repository.findById(session.getId())).thenReturn(Optional.empty());
            case "wrong-owner" -> user.setId(UUID.randomUUID());
            case "disabled" -> user.setStatus(UserStatus.DISABLED);
            case "locked" -> user.setStatus(UserStatus.LOCKED);
            case "changed-role" -> user.setRole(UserRole.PATIENT);
        }
        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"subject", "id", "role", "expiry", "invalid-subject", "invalid-id", "unknown-role", "wrong-issuer", "expired"})
    void invalidClaimsAreRejectedBeforeSessionLookup(String reason) {
        var builder = Jwts.builder().header().keyId("test-key").and().audience().add("careflow-api").and().claim("cnf", java.util.Map.of("jkt",session.getDpopJkt())).subject(user.getId().toString()).id(session.getId().toString())
                .claim("role", "DOCTOR").issuer("careflow-identity")
                .expiration(Date.from(Instant.now().plusSeconds(3600)));
        switch (reason) {
            case "subject" -> builder.subject(null);
            case "id" -> builder.id(null);
            case "role" -> builder.claim("role", null);
            case "expiry" -> builder.expiration(null);
            case "invalid-subject" -> builder.subject("invalid");
            case "invalid-id" -> builder.id("invalid");
            case "unknown-role" -> builder.claim("role", "SUPERUSER");
            case "wrong-issuer" -> builder.issuer("untrusted");
            case "expired" -> builder.expiration(Date.from(Instant.now().minusSeconds(10)));
        }
        String token = builder.signWith(SigningTestKeys.keys().privateKey(),Jwts.SIG.RS256).compact();
        assertThatThrownBy(() -> service.parse(token)).isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
        verify(repository, never()).findById(any());
    }

    @Test
    void effectiveExpiryCannotOutliveBackingSession() {
        session.setExpiresAt(Instant.now().plusSeconds(30));
        String token = service.issue(user, session.getId());
        var principal = service.parse(token);
        assertThat(principal.sessionId()).isEqualTo(session.getId());
        assertThat(principal.expiresAt()).isEqualTo(session.getExpiresAt());
    }
}
