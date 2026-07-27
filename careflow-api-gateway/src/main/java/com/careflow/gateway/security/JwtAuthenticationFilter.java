package com.careflow.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
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

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLE = "X-User-Role";
    private static final String CORRELATION_ID = "X-Correlation-Id";
    private static final Set<String> ROLES = Set.of("PATIENT", "DOCTOR", "ADMIN");

    private final SecretKey key;
    private final String issuer;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret,
                                   @Value("${jwt.issuer:careflow-identity}") String issuer,
                                   ObjectMapper objectMapper) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String correlationId = normalizedCorrelationId(
                exchange.getRequest().getHeaders().getFirst(CORRELATION_ID));
        ServerHttpRequest.Builder request = exchange.getRequest().mutate().headers(headers -> {
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
        try {
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                    .parseSignedClaims(authorization.substring(7)).getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            String role = claims.get("role", String.class);
            if (!ROLES.contains(role)) throw new JwtException("Invalid role");
            request.headers(headers -> {
                headers.set(USER_ID, userId.toString());
                headers.set(USER_ROLE, role);
            });
            return chain.filter(exchange.mutate().request(request.build()).build());
        } catch (JwtException | IllegalArgumentException exception) {
            return unauthorized(exchange, correlationId, "JWT không hợp lệ hoặc đã hết hạn");
        }
    }

    private boolean isPublic(HttpMethod method, String path) {
        return HttpMethod.OPTIONS.equals(method)
                || (HttpMethod.POST.equals(method) && Set.of(
                        "/api/auth/register", "/api/auth/login",
                        "/api/auth/refresh", "/api/auth/logout",
                        "/api/auth/forgot-password", "/api/auth/reset-password").contains(path))
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
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(CORRELATION_ID, correlationId);
        try {
            byte[] body = objectMapper.writeValueAsBytes(Map.of(
                    "status", 401,
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
