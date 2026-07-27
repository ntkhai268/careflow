package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.RefreshToken;
import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.repository.RefreshTokenRepository;
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
    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(tokens, 86_400_000L, 2_592_000_000L);
    }

    @Test
    void issueReturnsOpaqueTokenAndStoresOnlyItsHash() {
        User user = activeUser();

        RefreshTokenService.IssuedRefreshToken issued = service.issue(user, false);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(tokenCaptor.capture());
        RefreshToken stored = tokenCaptor.getValue();
        assertThat(issued.value()).hasSize(43);
        assertThat(stored.getTokenHash()).hasSize(64).doesNotContain(issued.value());
        assertThat(stored.getUser()).isSameAs(user);
        assertThat(issued.expiresInSeconds()).isEqualTo(86_400L);
        assertThat(stored.isPersistentSession()).isFalse();
    }

    @Test
    void rememberMeCreatesThirtyDayPersistentSession() {
        User user = activeUser();

        RefreshTokenService.IssuedRefreshToken issued = service.issue(user, true);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(tokenCaptor.capture());
        assertThat(issued.expiresInSeconds()).isEqualTo(2_592_000L);
        assertThat(issued.persistentSession()).isTrue();
        assertThat(tokenCaptor.getValue().isPersistentSession()).isTrue();
    }

    @Test
    void rotateRevokesCurrentTokenAndIssuesAReplacement() {
        User user = activeUser();
        RefreshToken current = activeToken(user);
        current.setPersistentSession(true);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(current));

        RefreshTokenService.RotatedRefreshToken rotated = service.rotate("old-refresh-token");

        assertThat(rotated.value()).isNotBlank().isNotEqualTo("old-refresh-token");
        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedByTokenHash()).hasSize(64);
        assertThat(rotated.persistentSession()).isTrue();
        assertThat(rotated.expiresInSeconds()).isEqualTo(2_592_000L);
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

        assertThatThrownBy(() -> service.rotate("reused-refresh-token"))
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

        assertThatThrownBy(() -> service.rotate("expired-refresh-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(401);
        assertThat(expired.isRevoked()).isTrue();
        verify(tokens).save(expired);
    }

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private RefreshToken activeToken(User user) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash("a".repeat(64));
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }
}
