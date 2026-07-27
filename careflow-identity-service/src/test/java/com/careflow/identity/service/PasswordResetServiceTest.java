package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.PasswordResetToken;
import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.dto.ForgotPasswordResponse;
import com.careflow.identity.repository.PasswordResetTokenRepository;
import com.careflow.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock UserRepository users;
    @Mock PasswordResetTokenRepository resetTokens;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshTokenService refreshTokens;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                users, resetTokens, passwordEncoder, refreshTokens, 900_000L, true);
    }

    @Test
    void requestStoresOnlyHashAndExposesTokenOnlyInDevelopmentMode() {
        User user = activeUser();
        when(users.findByEmailIgnoreCase("patient@example.com")).thenReturn(Optional.of(user));
        when(resetTokens.findAllByUserIdAndUsedAtIsNull(user.getId())).thenReturn(List.of());

        ForgotPasswordResponse response = service.request(" PATIENT@example.com ");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(captor.capture());
        assertThat(response.resetToken()).hasSize(43);
        assertThat(captor.getValue().getTokenHash()).hasSize(64)
                .doesNotContain(response.resetToken());
        assertThat(response.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void unknownEmailReturnsSameAcceptedShapeWithoutCreatingToken() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        ForgotPasswordResponse response = service.request("missing@example.com");

        assertThat(response.resetToken()).isNull();
        assertThat(response.expiresAt()).isNull();
        verifyNoInteractions(resetTokens, refreshTokens);
    }

    @Test
    void productionModeNeverReturnsRawTokenForKnownEmail() {
        User user = activeUser();
        when(users.findByEmailIgnoreCase("patient@example.com")).thenReturn(Optional.of(user));
        when(resetTokens.findAllByUserIdAndUsedAtIsNull(user.getId())).thenReturn(List.of());
        PasswordResetService productionService = new PasswordResetService(
                users, resetTokens, passwordEncoder, refreshTokens, 900_000L, false);

        ForgotPasswordResponse response = productionService.request("patient@example.com");

        assertThat(response.resetToken()).isNull();
        assertThat(response.expiresAt()).isNull();
        verify(resetTokens).save(any(PasswordResetToken.class));
    }

    @Test
    void validTokenChangesPasswordUnlocksUserAndRevokesSessions() {
        User user = activeUser();
        user.setStatus(UserStatus.LOCKED);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().plusSeconds(600));
        PasswordResetToken token = activeToken(user);
        when(resetTokens.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(resetTokens.findAllByUserIdAndUsedAtIsNull(user.getId())).thenReturn(List.of(token));
        when(passwordEncoder.encode("NewStrong1!")).thenReturn("new-hash");

        service.reset("raw-token", "NewStrong1!");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(token.isUsed()).isTrue();
        verify(refreshTokens).revokeAll(user);
    }

    @Test
    void expiredTokenIsConsumedAndRejected() {
        PasswordResetToken token = activeToken(activeUser());
        token.setExpiresAt(Instant.now().minusSeconds(1));
        when(resetTokens.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.reset("expired", "NewStrong1!"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(400);
        assertThat(token.isUsed()).isTrue();
        verify(resetTokens).save(token);
        verifyNoInteractions(passwordEncoder, refreshTokens);
    }

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("patient@example.com");
        user.setPasswordHash("old-hash");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private PasswordResetToken activeToken(User user) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash("a".repeat(64));
        token.setExpiresAt(Instant.now().plusSeconds(900));
        return token;
    }
}
