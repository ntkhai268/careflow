package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.PasswordResetToken;
import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.dto.ForgotPasswordResponse;
import com.careflow.identity.repository.PasswordResetTokenRepository;
import com.careflow.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PasswordResetService {
    private static final int TOKEN_BYTES = 32;

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final long expirationMs;
    private final boolean exposeToken;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokens,
            @Value("${identity.password-reset.expiration:900000}") long expirationMs,
            @Value("${identity.password-reset.expose-token:false}") boolean exposeToken) {
        if (expirationMs <= 0) throw new IllegalArgumentException("Password reset expiration must be positive");
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.expirationMs = expirationMs;
        this.exposeToken = exposeToken;
    }

    @Transactional
    public ForgotPasswordResponse request(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) return ForgotPasswordResponse.accepted();

        Instant now = Instant.now();
        invalidateActiveTokens(user, now);
        String rawToken = newToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(now.plusMillis(expirationMs));
        resetTokens.save(token);

        return exposeToken
                ? new ForgotPasswordResponse(rawToken, token.getExpiresAt())
                : ForgotPasswordResponse.accepted();
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void reset(String rawToken, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken token = resetTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(this::invalidToken);
        if (token.isUsed()) throw invalidToken();
        if (token.isExpired(now)) {
            token.setUsedAt(now);
            resetTokens.save(token);
            throw invalidToken();
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) user.setStatus(UserStatus.ACTIVE);
        users.save(user);

        invalidateActiveTokens(user, now);
        token.setUsedAt(now);
        resetTokens.save(token);
        refreshTokens.revokeAll(user);
    }

    private void invalidateActiveTokens(User user, Instant now) {
        var activeTokens = resetTokens.findAllByUserIdAndUsedAtIsNull(user.getId());
        activeTokens.forEach(token -> token.setUsedAt(now));
        resetTokens.saveAll(activeTokens);
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    private BusinessException invalidToken() {
        return new BusinessException(400, "Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
    }
}
