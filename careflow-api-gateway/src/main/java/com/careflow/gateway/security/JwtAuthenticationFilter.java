package com.careflow.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.ActiveSessionClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLE = "X-User-Role";
    private static final String CORRELATION_ID = "X-Correlation-Id";
    private static final Set<String> ROLES = Set.of(
            "PATIENT", "DOCTOR", "STAFF", "LAB_TECHNICIAN", "ADMIN");

    private final ObjectMapper objectMapper;
    private final ActiveSessionClient sessions;

    public JwtAuthenticationFilter(ObjectMapper objectMapper, ActiveSessionClient sessions) {
        this.objectMapper = objectMapper; this.sessions = sessions;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String correlationId = normalizedCorrelationId(
                exchange.getRequest().getHeaders().getFirst(CORRELATION_ID));
        ServerHttpRequest.Builder request = exchange.getRequest().mutate().headers(headers -> {
            for (String name : Set.of("X-Careflow-Caller","X-Careflow-Caller-Key","X-Careflow-Request-Method","X-Careflow-Request-Path","X-Careflow-Delegated-Caller","X-Careflow-Delegated-Key")) headers.remove(name);
            headers.remove(USER_ID);
            headers.remove(USER_ROLE);
            headers.remove(CORRELATION_ID);
            headers.set(CORRELATION_ID, correlationId);
        });

        if (isPublic(exchange.getRequest().getMethod(), path)) {
            return chain.filter(exchange.mutate().request(request.build()).build());
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7) {
            return unauthorized(exchange, correlationId, "Thiếu Bearer token");
        }
        // Identity is the authoritative verifier. Gateway holds no token-signing secret.
        // Final resource services independently validate the client proof and consume its replay ID.
        return Mono.fromCallable(() -> sessions.verify(authorization, correlationId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(principal -> {
                    request.headers(headers -> {
                        headers.set(USER_ID, principal.userId().toString());
                        headers.set(USER_ROLE, principal.role());
                    });
                    return chain.filter(exchange.mutate().request(request.build()).build());
                })
                .onErrorResume(BusinessException.class, exception -> authenticationError(exchange, correlationId,
                        exception.getStatus(), exception.getMessage()));
    }

    private boolean isPublic(HttpMethod method, String path) {
        return (HttpMethod.GET.equals(method) && path.equals("/api/auth/jwks"))
                || HttpMethod.OPTIONS.equals(method)
                || (HttpMethod.GET.equals(method) && path.equals("/api/directory/departments"))
                || (HttpMethod.GET.equals(method) && path.matches("/api/directory/departments/[^/]+"))
                || (HttpMethod.GET.equals(method) && path.equals("/api/directory/rooms"))
                || (HttpMethod.GET.equals(method) && path.equals("/api/directory/doctors"))
                || (HttpMethod.POST.equals(method) && Set.of(
                        "/api/auth/register", "/api/auth/registration/email/request", "/api/auth/registration/email/verify",
            "/api/auth/staff-passkeys/enroll/options", "/api/auth/staff-passkeys/enroll/finish", "/api/auth/mail/events", "/api/auth/login", "/api/auth/login/biometric",
            "/api/auth/recovery/email/request","/api/auth/recovery/email/verify", "/api/auth/recovery/email/confirm",
                        "/api/auth/login/question", "/api/auth/recovery/question",
                                "/api/auth/passkeys/login/options", "/api/auth/passkeys/login/finish",
                                "/api/auth/recovery/passkey/options", "/api/auth/recovery/passkey/finish",
                                "/api/auth/refresh", "/api/auth/logout").contains(path))
                || path.startsWith("/actuator/health")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/")
                || path.equals("/v3/api-docs")
                || path.equals("/ws") || path.startsWith("/ws/");
    }

    private String normalizedCorrelationId(String value) {
        try {
            return value == null ? UUID.randomUUID().toString() : UUID.fromString(value).toString();
        } catch (IllegalArgumentException ignored) {
            return UUID.randomUUID().toString();
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String correlationId, String message) {
        return authenticationError(exchange, correlationId, 401, message);
    }

    private Mono<Void> authenticationError(ServerWebExchange exchange, String correlationId, int status, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(CORRELATION_ID, correlationId);
        try {
            byte[] body = objectMapper.writeValueAsBytes(Map.of(
                    "status", status,
                    "message", message,
                    "timestamp", Instant.now().toString()));
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception exception) {
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
