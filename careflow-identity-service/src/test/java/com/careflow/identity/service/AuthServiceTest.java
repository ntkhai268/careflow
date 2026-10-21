package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.dto.*;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokens;
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwordEncoder, jwtService, refreshTokens);
    }

    @Test
    void publicRegistrationAlwaysCreatesNormalizedPatient() {
        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "NguyenVanA", "VANA@EXAMPLE.COM", "StrongPassword1!")))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(410);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void duplicateRegistrationReturnsConflict() {
        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "NguyenVanA", "vana@example.com", "StrongPassword1!")))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(410);
    }

    @Test
    void fifthWrongPasswordTemporarilyLocksAccount() {
        User user = activeUser();
        user.setFailedLoginAttempts(4);
        when(users.findByUsernameIgnoreCaseOrEmailIgnoreCase("patient", "patient")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("patient", "wrong"), "test-binding"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(401);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isNotNull();
        verify(users).save(user);
    }

    @Test
    void successfulLoginResetsFailuresAndReturnsJwt() {
        User user = activeUser();
        user.setFailedLoginAttempts(3);
        when(users.findByUsernameIgnoreCaseOrEmailIgnoreCase("patient", "patient")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        UUID sessionId = UUID.randomUUID();
        when(jwtService.issue(user, sessionId)).thenReturn("jwt");
        when(jwtService.expirationSeconds()).thenReturn(86400L);
        when(refreshTokens.issue(user, "test-binding"))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh", 2592000L, sessionId));

        LoginResponse response = service.login(new LoginRequest("patient", "correct"), "test-binding");

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.refreshTokenExpiresInSeconds()).isEqualTo(2592000L);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void refreshRotatesTokenAndReturnsNewTokenPair() {
        User user = activeUser();
        UUID sessionId = UUID.randomUUID();
        when(refreshTokens.rotate("old-refresh", "test-binding"))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(user, "new-refresh", 2592000L, sessionId));
        when(jwtService.issue(user, sessionId)).thenReturn("new-jwt");
        when(jwtService.expirationSeconds()).thenReturn(86400L);

        LoginResponse response = service.refresh(new RefreshTokenRequest("old-refresh"), "test-binding");

        assertThat(response.accessToken()).isEqualTo("new-jwt");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokens).rotate("old-refresh", "test-binding");
    }

    @Test
    void logoutRevokesRefreshToken() {
        service.logout(new RefreshTokenRequest("refresh"), "test-binding");

        verify(refreshTokens).revoke("refresh", "test-binding");
    }

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("patient");
        user.setEmail("patient@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.PATIENT);
        user.setStatus(UserStatus.ACTIVE);
        lenient().when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        return user;
    }
}
