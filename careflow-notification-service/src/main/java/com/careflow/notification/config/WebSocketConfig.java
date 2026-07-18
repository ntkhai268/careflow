package com.careflow.notification.config;

import com.careflow.notification.security.JwtAuthenticator;
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

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtAuthenticator jwtAuthenticator;
    private final String[] allowedOrigins;

    public WebSocketConfig(JwtAuthenticator jwtAuthenticator,
                           @Value("${notification.allowed-origins:http://localhost:3000,http://localhost:5173}") String origins) {
        this.jwtAuthenticator = jwtAuthenticator;
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
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
                    accessor.setUser(jwtAuthenticator.authenticate(accessor.getFirstNativeHeader("Authorization")));
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeSubscription(accessor);
                }
                return message;
            }
        });
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("WebSocket chưa xác thực");
        }
        String destination = accessor.getDestination();
        if (destination == null) throw new AccessDeniedException("Destination không hợp lệ");
        if (destination.startsWith("/user/queue/notifications")) return;
        if (destination.startsWith("/topic/queues/departments/")) {
            boolean allowed = authentication.getAuthorities().stream().anyMatch(authority ->
                    authority.getAuthority().equals("ROLE_DOCTOR") || authority.getAuthority().equals("ROLE_ADMIN"));
            if (allowed) return;
        }
        throw new AccessDeniedException("Không có quyền subscribe destination này");
    }
}
