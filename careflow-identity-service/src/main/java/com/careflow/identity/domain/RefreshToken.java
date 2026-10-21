package com.careflow.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "dpop_jkt", length = 43)
    private String dpopJkt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "authentication_method", nullable = false, length = 16)
    private String authenticationMethod="PASSWORD";

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "authenticated_at", nullable = false)
    private Instant authenticatedAt=Instant.now();

    @Column(name = "last_interactive_at", nullable = false)
    private Instant lastInteractiveAt=authenticatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_hash", length = 64)
    private String replacedByTokenHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant now, String replacementHash) {
        revokedAt = now;
        replacedByTokenHash = replacementHash;
    }

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }
}
