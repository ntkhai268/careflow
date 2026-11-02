package com.careflow.identity.registration;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.dto.RegisterRequest;
import com.careflow.identity.recovery.RecoveryEmailSender;
import com.careflow.identity.recovery.RecoveryMailOutbox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class EmailRegistrationService {
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int CHALLENGE_TTL_SECONDS = 300;
    private static final int MAX_SENDS_PER_HOUR = 5;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final RecoveryEmailSender sender;
    private final RecoveryMailOutbox outbox;
    private final SecureRandom random = new SecureRandom();

    public EmailRegistrationService(JdbcTemplate jdbc, PasswordEncoder passwords,
                                    RecoveryEmailSender sender, RecoveryMailOutbox outbox) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.sender = sender;
        this.outbox = outbox;
    }

    public record Requested(UUID requestId, int expiresInSeconds, int resendAfterSeconds, String message) {
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public Requested request(RegisterRequest request, String jkt) {
        proof(jkt);
        if (!sender.configured()) {
            throw new BusinessException(503, "Dịch vụ xác minh email chưa sẵn sàng.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(400, "Mật khẩu tối đa 72 byte UTF-8.");
        }
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        String username = request.username().strip().toLowerCase(Locale.ROOT);
        String fingerprint = hash(email);

        jdbc.update("INSERT INTO identity.signup_challenges(email_fingerprint) VALUES (?) ON CONFLICT DO NOTHING", fingerprint);
        var row = jdbc.queryForMap("SELECT * FROM identity.signup_challenges WHERE email_fingerprint=? FOR UPDATE", fingerprint);
        Instant now = Instant.now();
        if (row.get("last_sent_at") instanceof Timestamp last
                && last.toInstant().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            throw new BusinessException(429, "Vui lòng chờ 60 giây trước khi gửi lại mã.");
        }
        int sends = ((Number) row.get("send_count")).intValue();
        Instant window = ((Timestamp) row.get("window_started_at")).toInstant();
        if (!window.plusSeconds(3600).isAfter(now)) {
            sends = 0;
            window = now;
        }
        if (sends >= MAX_SENDS_PER_HOUR) {
            throw new BusinessException(429, "Đã đạt giới hạn gửi mã. Vui lòng thử lại sau.");
        }

        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String codeHash = passwords.encode(code);
        String passwordHash = passwords.encode(request.password());
        boolean eligible = !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM identity.users WHERE lower(email)=? OR lower(username)=?)",
                Boolean.class, email, username));

        UUID id = UUID.randomUUID();
        jdbc.update(
                "UPDATE identity.signup_challenges SET request_id=?,email=?,username=?,password_hash=?,code_hash=?,dpop_jkt=?,eligible=?,expires_at=?,used_at=NULL,failed_attempts=0,delivery_state='PENDING',last_sent_at=?,window_started_at=?,send_count=? WHERE email_fingerprint=?",
                id,
                email,
                username,
                passwordHash,
                codeHash,
                jkt,
                eligible,
                Timestamp.from(now.plusSeconds(CHALLENGE_TTL_SECONDS)),
                Timestamp.from(now),
                Timestamp.from(window),
                sends + 1,
                fingerprint);
        if (eligible) {
            outbox.enqueue(id, "SIGNUP", email, code);
        }
        return new Requested(
                id,
                CHALLENGE_TTL_SECONDS,
                RESEND_COOLDOWN_SECONDS,
                "Nếu thông tin phù hợp để đăng ký, mã xác minh sẽ được gửi tới email của bạn.");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void verify(UUID requestId, String code, String jkt) {
        proof(jkt);
        var rows = jdbc.queryForList("SELECT * FROM identity.signup_challenges WHERE request_id=? FOR UPDATE", requestId);
        if (rows.isEmpty()) {
            throw invalid();
        }
        var row = rows.getFirst();
        if (row.get("used_at") != null
                || row.get("code_hash") == null
                || ((Number) row.get("failed_attempts")).intValue() >= MAX_FAILED_ATTEMPTS
                || !jkt.equals(row.get("dpop_jkt"))
                || !((Timestamp) row.get("expires_at")).toInstant().isAfter(Instant.now())) {
            throw invalid();
        }
        if (!passwords.matches(code, (String) row.get("code_hash"))) {
            jdbc.update("UPDATE identity.signup_challenges SET failed_attempts=failed_attempts+1 WHERE request_id=?", requestId);
            throw invalid();
        }
        if (!Boolean.TRUE.equals(row.get("eligible")) || !"SENT".equals(row.get("delivery_state"))) {
            throw invalid();
        }
        // A uniqueness race is a failed verification, not a duplicate account or a leaked DB error.
        int inserted = jdbc.update(
                "INSERT INTO identity.users(id,username,email,password_hash,role,status,email_verified_at,created_at,updated_at) VALUES (?,?,?,?,'PATIENT','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING",
                UUID.randomUUID(), row.get("username"), row.get("email"), row.get("password_hash"));
        jdbc.update(
                "UPDATE identity.signup_challenges SET used_at=CURRENT_TIMESTAMP,email=NULL,username=NULL,password_hash=NULL,code_hash=NULL WHERE request_id=?",
                requestId);
        if (inserted != 1) {
            throw invalid();
        }
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void proof(String jkt) {
        if (jkt == null || jkt.isBlank()) {
            throw new BusinessException(401, "Yêu cầu xác thực thiết bị.");
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(400, "Mã không hợp lệ, đã hết hạn hoặc thông tin đăng ký không còn khả dụng. Vui lòng thử lại.");
    }
}
