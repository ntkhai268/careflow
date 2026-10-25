package com.careflow.notification.config;

import com.careflow.notification.security.JwtAuthenticator;
import com.careflow.notification.security.WebSocketSessionGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtAuthenticator jwtAuthenticator;
    private final String[] allowedOrigins;
    private final WebSocketSessionGuard sessions;

    public WebSocketConfig(JwtAuthenticator jwtAuthenticator, WebSocketSessionGuard sessions,
                           @Value("${notification.allowed-origins:http://localhost:3000,http://localhost:5173}") String origins) {
        this.jwtAuthenticator = jwtAuthenticator;
        this.sessions = sessions;
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authorization = accessor.getFirstNativeHeader("Authorization");
                    accessor.setUser(jwtAuthenticator.authenticateConnect(authorization, accessor.getFirstNativeHeader("DPoP")));
                    sessions.authenticated(accessor.getSessionId(), authorization);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    if (!sessions.active(accessor.getSessionId())) throw new AccessDeniedException("Phiên đã hết hiệu lực");
                    authorizeSubscription(accessor);
                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    throw new AccessDeniedException("WebSocket chỉ hỗ trợ nhận thông báo cá nhân");
                } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    sessions.closed(accessor.getSessionId());
                }
                return message;
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(sessions);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.opened(session);
                try { super.afterConnectionEstablished(session); }
                catch (Exception failure) { sessions.closed(session.getId()); throw failure; }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.closed(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("WebSocket chưa xác thực");
        }
        String destination = accessor.getDestination();
        if (destination == null) throw new AccessDeniedException("Destination không hợp lệ");
        if (destination.equals("/user/queue/notifications")) return;
        throw new AccessDeniedException("Không có quyền subscribe destination này");
    }
}
