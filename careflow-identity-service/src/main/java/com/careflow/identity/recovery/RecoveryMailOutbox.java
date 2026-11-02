package com.careflow.identity.recovery;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class RecoveryMailOutbox {
    private static final Logger log = LoggerFactory.getLogger(RecoveryMailOutbox.class);
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final JdbcTemplate jdbc;
    private final RecoveryEmailSender sender;
    private final ObjectMapper json;
    private final byte[] key;

    public RecoveryMailOutbox(JdbcTemplate jdbc, RecoveryEmailSender sender, ObjectMapper json,
                              @Value("${recovery.outbox-key-file:}") String keyFile) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.json = json;
        try {
            this.key = keyFile.isBlank()
                    ? null
                    : Base64.getDecoder().decode(Files.readString(Path.of(keyFile)).strip());
            if (this.key != null && this.key.length != 32) {
                throw new IllegalArgumentException();
            }
        } catch (Exception failure) {
            throw new IllegalArgumentException("Recovery outbox requires a private base64 AES-256 key file");
        }
        if (sender.configured() && this.key == null) {
            throw new IllegalArgumentException("Configure RECOVERY_OUTBOX_KEY_FILE before enabling mail");
        }
    }

    @Transactional
    public void enqueue(UUID requestId, String kind, String email, String code) {
        if (key == null) {
            throw new BusinessException(503, "Dịch vụ gửi email chưa sẵn sàng.");
        }
        UUID id = UUID.randomUUID();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(id.toString().getBytes(StandardCharsets.US_ASCII));
            byte[] encrypted = cipher.doFinal(json.writeValueAsBytes(Map.of("email", email, "code", code == null ? "" : code)));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, NONCE_BYTES);
            System.arraycopy(encrypted, 0, payload, NONCE_BYTES, encrypted.length);

            long ttlSeconds = Set.of("CODE", "SIGNUP").contains(kind)
                    ? 300
                    : ("STAFF_INVITE".equals(kind) ? 900 : 86400);
            jdbc.update(
                    "INSERT INTO identity.recovery_mail_outbox(id,challenge_id,kind,payload,expires_at) VALUES (?,?,?,?,?)",
                    id, requestId, kind, payload, Timestamp.from(Instant.now().plusSeconds(ttlSeconds)));
        } catch (Exception failure) {
            throw new BusinessException(503, "Không thể chuẩn bị email bảo mật.");
        }
    }

    @Scheduled(fixedDelayString = "${recovery.outbox-poll-ms:1000}")
    @Transactional
    public void deliver() {
        if (key == null) {
            return;
        }
        var rows = jdbc.queryForList(
                "SELECT * FROM identity.recovery_mail_outbox WHERE state='PENDING' AND available_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED");
        if (rows.isEmpty()) {
            return;
        }
        var row = rows.getFirst();
        UUID id = (UUID) row.get("id");
        UUID requestId = (UUID) row.get("challenge_id");
        String kind = (String) row.get("kind");
        if (!((Timestamp) row.get("expires_at")).toInstant().isAfter(Instant.now()) || !validPurpose(kind, requestId)) {
            finish(id, "EXPIRED");
            return;
        }
        try {
            byte[] payload = (byte[]) row.get("payload");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, Arrays.copyOf(payload, NONCE_BYTES)));
            cipher.updateAAD(id.toString().getBytes(StandardCharsets.US_ASCII));
            var data = json.readTree(cipher.doFinal(Arrays.copyOfRange(payload, NONCE_BYTES, payload.length)));
            String provider = sender.sendTracked(kind, data.get("email").asText(), data.get("code").asText(), requestId);
            jdbc.update("UPDATE identity.recovery_mail_outbox SET provider_message_id=?,delivery_status='ACCEPTED' WHERE id=?", provider, id);
            if (provider != null) {
                MailDeliveryTracker.reconcile(jdbc, provider);
            }
            if ("CODE".equals(kind)) {
                jdbc.update("UPDATE identity.password_reset_codes SET delivery_state='SENT' WHERE challenge_id=?", requestId);
            }
            if ("SIGNUP".equals(kind)) {
                jdbc.update("UPDATE identity.signup_challenges SET delivery_state='SENT' WHERE request_id=?", requestId);
            }
            finish(id, "SENT");
        } catch (Exception failure) {
            int attempts = ((Number) row.get("attempts")).intValue() + 1;
            log.warn("event=RECOVERY_MAIL_RETRY id={} attempt={}", id, attempts);
            if (attempts >= 5) {
                finish(id, "FAILED");
                if ("CODE".equals(kind)) {
                    jdbc.update("UPDATE identity.password_reset_codes SET delivery_state='FAILED' WHERE challenge_id=?", requestId);
                }
                if ("SIGNUP".equals(kind)) {
                    jdbc.update("UPDATE identity.signup_challenges SET delivery_state='FAILED' WHERE request_id=?", requestId);
                }
            } else {
                jdbc.update(
                        "UPDATE identity.recovery_mail_outbox SET attempts=?,available_at=CURRENT_TIMESTAMP+(? * interval '1 second') WHERE id=?",
                        attempts, Math.min(60, 1 << attempts), id);
            }
        }
    }

    private boolean validPurpose(String kind, UUID id) {
        if ("CODE".equals(kind)) {
            return !jdbc.queryForList(
                    "SELECT challenge_id FROM identity.password_reset_codes WHERE challenge_id=? AND used_at IS NULL AND verified_at IS NULL AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",
                    id).isEmpty();
        }
        if ("SIGNUP".equals(kind)) {
            return !jdbc.queryForList(
                    "SELECT request_id FROM identity.signup_challenges WHERE request_id=? AND eligible=true AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",
                    id).isEmpty();
        }
        if ("STAFF_INVITE".equals(kind)) {
            return !jdbc.queryForList(
                    "SELECT id FROM identity.staff_passkey_grants WHERE id=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",
                    id).isEmpty();
        }
        return "CHANGED".equals(kind);
    }

    private void finish(UUID id, String state) {
        jdbc.update(
                "UPDATE identity.recovery_mail_outbox SET state=?,payload=NULL,delivery_status=CASE WHEN ?='FAILED' THEN 'FAILED' WHEN ?='EXPIRED' THEN 'EXPIRED' ELSE delivery_status END WHERE id=?",
                state, state, state, id);
    }

    @Scheduled(fixedDelay = 3600000)
    public void cleanup() {
        jdbc.update("UPDATE identity.recovery_mail_outbox SET state='EXPIRED',payload=NULL WHERE state='PENDING' AND expires_at<CURRENT_TIMESTAMP");
        jdbc.update("DELETE FROM identity.recovery_mail_outbox WHERE state<>'PENDING' AND created_at<CURRENT_TIMESTAMP-interval '1 day'");
        jdbc.update("DELETE FROM identity.auth_rate_windows WHERE expires_at<CURRENT_TIMESTAMP");
        jdbc.update("DELETE FROM identity.dpop_replays WHERE expires_at<CURRENT_TIMESTAMP");
        jdbc.update("DELETE FROM identity.signup_challenges WHERE last_sent_at<CURRENT_TIMESTAMP-interval '1 day'");
        jdbc.update("DELETE FROM identity.mail_delivery_events WHERE received_at<CURRENT_TIMESTAMP-interval '7 days'");
    }
}
