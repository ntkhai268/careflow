package com.careflow.identity.security;

import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserRole;
import com.careflow.identity.domain.RefreshToken;
import com.careflow.identity.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private static final String SECRET = "a-very-long-test-secret-that-is-at-least-thirty-two-bytes";

    @Test
    void issuedTokenRoundTripsTrustedIdentity() {
        RefreshTokenRepository sessions = mock(RefreshTokenRepository.class);
        JwtService service = new JwtService(SigningTestKeys.keys(), 60_000, "careflow-identity", "careflow-api", sessions);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(UserRole.DOCTOR);

        RefreshToken session = new RefreshToken();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setDpopJkt("test-client-key-thumbprint");
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        JwtService.AuthenticatedUser parsed = service.parse(service.issue(user, session.getId()));

        assertThat(parsed.userId()).isEqualTo(user.getId());
        assertThat(parsed.role()).isEqualTo(UserRole.DOCTOR);
    }
}
