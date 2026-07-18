package com.careflow.notification.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtAuthenticator {
    private static final Set<String> ROLES = Set.of("PATIENT", "DOCTOR", "ADMIN");
    private final SecretKey key;
    private final String issuer;

    public JwtAuthenticator(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.issuer:careflow-identity}") String issuer) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("JWT secret too short");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    public UsernamePasswordAuthenticationToken authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Thiếu Bearer token trong STOMP CONNECT");
        }
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                .parseSignedClaims(authorization.substring(7)).getPayload();
        UUID userId = UUID.fromString(claims.getSubject());
        String role = claims.get("role", String.class);
        if (!ROLES.contains(role)) throw new IllegalArgumentException("JWT role không hợp lệ");
        return new UsernamePasswordAuthenticationToken(userId.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
