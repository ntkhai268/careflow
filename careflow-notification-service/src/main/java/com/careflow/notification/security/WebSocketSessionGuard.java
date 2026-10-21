package com.careflow.notification.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Tokens stay in this server-side registry and never enter STOMP output headers. */
@Component
public class WebSocketSessionGuard implements ChannelInterceptor {
    private final JwtAuthenticator authenticator;
    private final ConcurrentHashMap<String, WebSocketSession> transports = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> authorizations = new ConcurrentHashMap<>();

    public WebSocketSessionGuard(JwtAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public void opened(WebSocketSession session) {
        transports.put(session.getId(), session);
    }

    public void closed(String sessionId) {
        transports.remove(sessionId);
        authorizations.remove(sessionId);
    }

    public void authenticated(String sessionId, String authorization) {
        if (sessionId == null || !transports.containsKey(sessionId)) {
            throw new AccessDeniedException("WebSocket transport chưa sẵn sàng");
        }
        authorizations.put(sessionId, authorization);
    }

    public boolean active(String sessionId) {
        String authorization = sessionId == null ? null : authorizations.get(sessionId);
        if (authorization == null) return false;
        try {
            authenticator.authenticate(authorization);
            return true;
        } catch (RuntimeException invalidOrUnavailable) {
            close(sessionId);
            return false;
        }
    }

    public void close(String sessionId) {
        WebSocketSession session = transports.get(sessionId);
        closed(sessionId);
        if (session != null) {
            try { session.close(CloseStatus.POLICY_VIOLATION); }
            catch (IOException ignored) { /* Transport has already disconnected. */ }
        }
    }

    @Scheduled(fixedDelayString = "${notification.session-check-ms:1000}")
    public void revalidateSessions() {
        authorizations.keySet().forEach(this::active);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Broker-created outbound messages have SIMP headers, not necessarily a
        // STOMP accessor yet. Check the protocol-neutral type before serialization.
        if (SimpMessageType.MESSAGE.equals(SimpMessageHeaderAccessor.getMessageType(message.getHeaders()))
                && !active(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()))) return null;
        return message;
    }
}
