package com.careflow.identity.security;

import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserRole;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.repository.RefreshTokenRepository;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtSigningKeys keys;
    private final String audience;
    private final long expirationMs;
    private final String issuer;
    private final RefreshTokenRepository sessions;
    @Value("${security.auth.require-staff-passkey:false}") private boolean requireStaffPasskey;

    public JwtService(JwtSigningKeys keys,
                      @Value("${jwt.expiration:900000}") long expirationMs,
                      @Value("${jwt.issuer:careflow-identity}") String issuer,
                      @Value("${security.jwt.audience:careflow-api}") String audience,
                      RefreshTokenRepository sessions) {
        if (expirationMs <= 0 || audience.isBlank()) throw new IllegalArgumentException("JWT lifetime and audience are required");
        this.keys = keys; this.expirationMs = expirationMs; this.issuer = issuer; this.audience = audience; this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public String issue(User user, UUID sessionId) {
        var session = sessions.findById(sessionId).orElseThrow(() -> new JwtException("Unknown session"));
        if (session.getDpopJkt() == null) throw new JwtException("Session requires client-key binding");
        Instant now = Instant.now();
        return Jwts.builder().header().keyId(keys.kid()).and()
                .subject(user.getId().toString()).claim("role", user.getRole().name())
                .claim("cnf", java.util.Map.of("jkt",session.getDpopJkt()))
                .issuer(issuer).audience().add(audience).and().id(sessionId.toString())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(keys.privateKey(), Jwts.SIG.RS256).compact();
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser parse(String token) {
        var jwt = Jwts.parser()
                .keyLocator(header -> {
                    if(!"RS256".equals(header.get("alg"))) throw new JwtException("Unexpected signing algorithm");
                    if(!(header.get("kid") instanceof String keyId)) throw new JwtException("Missing signing key");
                    return keys.verificationKey(keyId);
                })
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token);
        if (!"RS256".equals(jwt.getHeader().getAlgorithm())) throw new JwtException("Unexpected signing key or algorithm");
        Claims claims = jwt.getPayload();
        String roleClaim = claims.get("role", String.class);
        if (claims.getSubject() == null || claims.getId() == null || claims.getExpiration() == null || roleClaim == null) {
            throw new JwtException("Missing authentication claims");
        }
        UUID userId = UUID.fromString(claims.getSubject());
        UUID sessionId = UUID.fromString(claims.getId());
        UserRole role = UserRole.valueOf(roleClaim);
        var session = sessions.findById(sessionId).orElseThrow(() -> new JwtException("Unknown session"));
        Instant now = Instant.now();
        User user = session.getUser();
        if (session.isRevoked() || session.isExpired(now) || InteractiveSessionPolicy.expired(session,now) || !userId.equals(user.getId())
                || user.getStatus() != UserStatus.ACTIVE || user.getRole() != role) {
            throw new JwtException("Session is no longer active");
        }
        if(requireStaffPasskey && role!=UserRole.PATIENT && !"PASSKEY".equals(session.getAuthenticationMethod()))
            throw new JwtException("Staff session requires passkey authentication");
        Object confirmation = claims.get("cnf");
        if (!(confirmation instanceof java.util.Map<?,?> binding) || session.getDpopJkt() == null
                || !session.getDpopJkt().equals(binding.get("jkt"))) throw new JwtException("Missing/mismatched client key binding");
        Instant expiresAt = claims.getExpiration().toInstant();
        if (session.getExpiresAt().isBefore(expiresAt)) expiresAt = session.getExpiresAt();
        return new AuthenticatedUser(userId, role, sessionId, expiresAt, session.getDpopJkt());
    }

    public long expirationSeconds() {
        return expirationMs / 1000;
    }

    public record AuthenticatedUser(UUID userId, UserRole role, UUID sessionId, Instant expiresAt, String dpopJkt) {
    }
}
