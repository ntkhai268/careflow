package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.RefreshToken;
import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final long expirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens,
                               @Value("${jwt.refresh-expiration:2592000000}") long expirationMs) {
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT refresh expiration must be positive");
        }
        this.tokens = tokens;
        this.expirationMs = expirationMs;
    }

    @Transactional
    public IssuedRefreshToken issue(User user) {
        return create(user, Instant.now());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public RotatedRefreshToken rotate(String rawToken) {
        Instant now = Instant.now();
        RefreshToken current = tokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> invalidToken("Refresh token không hợp lệ"));

        if (current.isRevoked()) {
            revokeActiveTokens(current.getUser(), now);
            throw invalidToken("Refresh token đã bị thu hồi");
        }
        if (current.isExpired(now)) {
            current.revoke(now, null);
            tokens.save(current);
            throw invalidToken("Refresh token đã hết hạn");
        }
        if (current.getUser().getStatus() != UserStatus.ACTIVE) {
            revokeActiveTokens(current.getUser(), now);
            throw new BusinessException(403, "Tài khoản không ở trạng thái hoạt động");
        }

        IssuedRefreshToken replacement = create(current.getUser(), now);
        current.revoke(now, hash(replacement.value()));
        tokens.save(current);
        return new RotatedRefreshToken(
                current.getUser(), replacement.value(), replacement.expiresInSeconds());
    }

    @Transactional
    public void revoke(String rawToken) {
        Instant now = Instant.now();
        tokens.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke(now, null);
                tokens.save(token);
            }
        });
    }

    private IssuedRefreshToken create(User user, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(now.plusMillis(expirationMs));
        tokens.save(token);
        return new IssuedRefreshToken(rawToken, expirationMs / 1000);
    }

    private void revokeActiveTokens(User user, Instant now) {
        var activeTokens = tokens.findAllByUserIdAndRevokedAtIsNull(user.getId());
        activeTokens.forEach(token -> token.revoke(now, null));
        tokens.saveAll(activeTokens);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private BusinessException invalidToken(String message) {
        return new BusinessException(401, message);
    }

    public record IssuedRefreshToken(String value, long expiresInSeconds) {
    }

    public record RotatedRefreshToken(User user, String value, long expiresInSeconds) {
    }
}
