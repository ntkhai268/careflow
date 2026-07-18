package com.careflow.queue.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.queue.domain.QueueEntry;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class QrTokenService {
    private static final String PURPOSE = "QUEUE_CHECK_IN";
    private final SecretKey key;
    private final long expirationMinutes;

    public QrTokenService(@Value("${queue.qr-secret:${jwt.secret}}") String secret,
                          @Value("${queue.qr-expiration-minutes:30}") long expirationMinutes) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("QR secret too short");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String issue(QueueEntry entry) {
        Instant now = Instant.now();
        return Jwts.builder().subject(entry.getAppointmentId().toString())
                .claim("userId", entry.getUserId().toString())
                .claim("queueDate", entry.getQueueDate().toString())
                .claim("purpose", PURPOSE).issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key).compact();
    }

    public QrClaims verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!PURPOSE.equals(claims.get("purpose", String.class))) throw new JwtException("Wrong purpose");
            return new QrClaims(UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("userId", String.class)),
                    LocalDate.parse(claims.get("queueDate", String.class)));
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(422, "QR token đã hết hạn");
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(401, "QR token không hợp lệ hoặc đã hết hạn");
        }
    }

    public record QrClaims(UUID appointmentId, UUID userId, LocalDate queueDate) {}
}
