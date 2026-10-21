package com.careflow.notification.security;

import com.careflow.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketSessionGuardTest {
    @Test
    void revokedSessionCannotReceiveEvenBeforePeriodicSweep() throws Exception {
        var authenticator = mock(JwtAuthenticator.class);
        var transport = mock(WebSocketSession.class);
        when(transport.getId()).thenReturn("socket-1");
        var guard = new WebSocketSessionGuard(authenticator);
        guard.opened(transport);
        guard.authenticated("socket-1", "Bearer private-token");
        when(authenticator.authenticate("Bearer private-token")).thenThrow(new BusinessException(401, "Revoked"));
        var headers = StompHeaderAccessor.create(StompCommand.MESSAGE);
        headers.setSessionId("socket-1");
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThat(guard.preSend(message, null)).isNull();
        verify(transport).close(CloseStatus.POLICY_VIOLATION);
        assertThat(guard.active("socket-1")).isFalse();
    }

    @Test
    void periodicSweepClosesIdleSessionWhenIdentityUnavailable() throws Exception {
        var authenticator = mock(JwtAuthenticator.class);
        var transport = mock(WebSocketSession.class);
        when(transport.getId()).thenReturn("idle");
        var guard = new WebSocketSessionGuard(authenticator);
        guard.opened(transport);
        guard.authenticated("idle", "Bearer private-token");
        when(authenticator.authenticate("Bearer private-token")).thenThrow(new BusinessException(503, "Unavailable"));
        guard.revalidateSessions();
        verify(transport).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void activeSessionIsRecheckedForEveryMessageAndRemovedOnDisconnect() {
        var authenticator = mock(JwtAuthenticator.class);
        var transport = mock(WebSocketSession.class);
        when(transport.getId()).thenReturn("active");
        var guard = new WebSocketSessionGuard(authenticator);
        guard.opened(transport);
        guard.authenticated("active", "Bearer private-token");
        assertThat(guard.active("active")).isTrue();
        assertThat(guard.active("active")).isTrue();
        verify(authenticator, times(2)).authenticate("Bearer private-token");
        guard.closed("active");
        assertThat(guard.active("active")).isFalse();
    }
}
