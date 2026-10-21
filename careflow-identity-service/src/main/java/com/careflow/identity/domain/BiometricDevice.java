package com.careflow.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="biometric_devices",schema="identity")
@Getter @Setter @NoArgsConstructor
public class BiometricDevice {
    @Id private UUID id;
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(name="secret_hash",length=64,nullable=false) private String secretHash;
    @Column(name="dpop_jkt",length=43,nullable=false) private String dpopJkt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="last_used_at") private Instant lastUsedAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
}
