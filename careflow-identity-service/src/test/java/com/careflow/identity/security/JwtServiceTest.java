package com.careflow.identity.security;

import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private static final String SECRET = "a-very-long-test-secret-that-is-at-least-thirty-two-bytes";

    @Test
    void issuedTokenRoundTripsTrustedIdentity() {
        JwtService service = new JwtService(SECRET, 60_000, "careflow-identity");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(UserRole.DOCTOR);

        JwtService.AuthenticatedUser parsed = service.parse(service.issue(user));

        assertThat(parsed.userId()).isEqualTo(user.getId());
        assertThat(parsed.role()).isEqualTo(UserRole.DOCTOR);
    }
}
