package com.careflow.queue.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.queue.domain.QueueEntry;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Service
public class QrTokenService {
    private static final String PURPOSE = "QUEUE_CHECK_IN";
    private static final String HOSPITAL_PURPOSE = "HOSPITAL_CHECK_IN";
    private final SecretKey key;
    private final ZoneId businessZone;
    private final long hospitalQrExpirationMinutes;

    public QrTokenService(String secret, String businessZone) {
        this(secret, businessZone, 30);
    }

    @Autowired
    public QrTokenService(@Value("${queue.qr-secret:${jwt.secret}}") String secret,
                          @Value("${queue.business-zone:Asia/Ho_Chi_Minh}") String businessZone,
                          @Value("${queue.qr-expiration-minutes:30}") long hospitalQrExpirationMinutes) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("QR secret too short");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.businessZone = ZoneId.of(businessZone);
        if (hospitalQrExpirationMinutes <= 0) throw new IllegalArgumentException("QR expiration must be positive");
        this.hospitalQrExpirationMinutes = hospitalQrExpirationMinutes;
    }

    public String issue(QueueEntry entry) {
        Instant now = Instant.now();
        Instant expiresAt = entry.getQueueDate().plusDays(1).atStartOfDay(businessZone).toInstant();
        return Jwts.builder().subject(entry.getAppointmentId().toString())
                .claim("ticketId", entry.getId().toString())
                .claim("userId", entry.getUserId().toString())
                .claim("queueDate", entry.getQueueDate().toString())
                .claim("purpose", PURPOSE).issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key).compact();
    }

    public QrClaims verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!PURPOSE.equals(claims.get("purpose", String.class))) throw new JwtException("Wrong purpose");
            return new QrClaims(UUID.fromString(claims.get("ticketId", String.class)),
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("userId", String.class)),
                    LocalDate.parse(claims.get("queueDate", String.class)));
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(422, "QR token đã hết hạn");
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(401, "QR token không hợp lệ hoặc đã hết hạn");
        }
    }

    public IssuedHospitalQr issueHospitalQr(String roomId, LocalDate sessionDate, String sessionCode) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(hospitalQrExpirationMinutes * 60);
        String token = Jwts.builder().subject(roomId)
                .claim("roomId", roomId)
                .claim("session", sessionCode)
                .claim("queueDate", sessionDate.toString())
                .claim("purpose", HOSPITAL_PURPOSE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key).compact();
        return new IssuedHospitalQr(token, expiresAt);
    }

    public HospitalQrClaims verifyHospitalQr(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!HOSPITAL_PURPOSE.equals(claims.get("purpose", String.class))) {
                throw new JwtException("Wrong purpose");
            }
            String roomId = claims.get("roomId", String.class);
            if (roomId == null || roomId.isBlank()) throw new JwtException("Missing room");
            return new HospitalQrClaims(
                    roomId,
                    claims.get("session", String.class),
                    LocalDate.parse(claims.get("queueDate", String.class)));
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(422, "QR bệnh viện đã hết hạn");
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(401, "QR bệnh viện không hợp lệ hoặc đã hết hạn");
        }
    }

    public record QrClaims(UUID ticketId, UUID appointmentId, UUID userId, LocalDate queueDate) {}

    public record HospitalQrClaims(String roomId, String sessionCode, LocalDate sessionDate) {}

    public record IssuedHospitalQr(String token, Instant expiresAt) {}
}
