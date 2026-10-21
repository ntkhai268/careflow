package com.careflow.identity.recovery;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="password_reset_codes", schema="identity")
@Getter @Setter @NoArgsConstructor
public class PasswordResetCode {
    @Id @Column(name="email_fingerprint", length=64) private String emailFingerprint;
    @Column(name="challenge_id", unique=true) private UUID challengeId;
    @Column(name="user_id") private UUID userId;
    @Column(name="code_hash", length=100) private String codeHash;
    @Column(name="dpop_jkt", length=43) private String dpopJkt;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="last_sent_at", nullable=false) private Instant lastSentAt;
    @Column(name="expires_at") private Instant expiresAt;
    @Column(name="used_at") private Instant usedAt;
    @Column(name="verified_at") private Instant verifiedAt;
    @Column(name="reset_token_hash", length=64) private String resetTokenHash;
    @Column(name="reset_expires_at") private Instant resetExpiresAt;
    @Column(name="failed_attempts", nullable=false) private int failedAttempts;
    @Column(name="window_started_at", nullable=false) private Instant windowStartedAt;
    @Column(name="send_count", nullable=false) private int sendCount;
    @Column(name="delivery_state", nullable=false, length=16) private String deliveryState;
}
