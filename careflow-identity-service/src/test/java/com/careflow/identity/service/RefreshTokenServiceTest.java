package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.RefreshToken;
import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.repository.RefreshTokenRepository;
import com.careflow.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    @Mock RefreshTokenRepository tokens;
    @Mock UserRepository users;
    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(tokens, users, org.mockito.Mockito.mock(com.careflow.identity.repository.BiometricDeviceRepository.class), 2_592_000_000L);
    }

    @Test
    void issueReturnsOpaqueTokenAndStoresOnlyItsHash() {
        User user = activeUser();

        RefreshTokenService.IssuedRefreshToken issued = service.issue(user, "test-binding");

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(tokenCaptor.capture());
        RefreshToken stored = tokenCaptor.getValue();
        assertThat(issued.value()).hasSize(43);
        assertThat(stored.getTokenHash()).hasSize(64).doesNotContain(issued.value());
        assertThat(stored.getUser()).isSameAs(user);
        assertThat(issued.expiresInSeconds()).isEqualTo(2_592_000L);
    }

    @Test
    void rotateRevokesCurrentTokenAndIssuesAReplacement() {
        User user = activeUser();
        RefreshToken current = activeToken(user);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(current));

        RefreshTokenService.RotatedRefreshToken rotated = service.rotate("old-refresh-token", "test-binding");

        assertThat(rotated.value()).isNotBlank().isNotEqualTo("old-refresh-token");
        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedByTokenHash()).hasSize(64);
        verify(tokens, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void reusingRevokedTokenRevokesAllActiveSessions() {
        User user = activeUser();
        RefreshToken reused = activeToken(user);
        reused.revoke(Instant.now(), "replacement-hash");
        RefreshToken activeSession = activeToken(user);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(reused));
        when(tokens.findAllByUserIdAndRevokedAtIsNull(user.getId()))
                .thenReturn(List.of(activeSession));

        assertThatThrownBy(() -> service.rotate("reused-refresh-token", "test-binding"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(401);
        assertThat(activeSession.isRevoked()).isTrue();
        verify(tokens).saveAll(List.of(activeSession));
    }

    @Test
    void expiredTokenIsRejectedAndRevoked() {
        User user = activeUser();
        RefreshToken expired = activeToken(user);
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("expired-refresh-token", "test-binding"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(401);
        assertThat(expired.isRevoked()).isTrue();
        verify(tokens).save(expired);
    }

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);
        lenient().when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        lenient().when(tokens.findOwnerIdByTokenHash(any())).thenReturn(Optional.of(user.getId()));
        return user;
    }

    private RefreshToken activeToken(User user) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setDpopJkt("test-binding");
        token.setTokenHash("a".repeat(64));
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }
}
