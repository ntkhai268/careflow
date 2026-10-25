package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.RefreshToken;
import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.repository.RefreshTokenRepository;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.repository.BiometricDeviceRepository;
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
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final long expirationMs;
    private final BiometricDeviceRepository devices;
    @Value("${security.auth.require-staff-passkey:false}") private boolean requireStaffPasskey;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, BiometricDeviceRepository devices,
                               @Value("${jwt.refresh-expiration:2592000000}") long expirationMs) {
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT refresh expiration must be positive");
        }
        this.devices = devices;
        this.tokens = tokens;
        this.users = users;
        this.expirationMs = expirationMs;
    }

    @Transactional
    public IssuedRefreshToken issue(User user, String dpopJkt) {
        User current = lockUser(user.getId());
        if (current.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(403, "Tài khoản không ở trạng thái hoạt động");
        }
        return create(current, Instant.now(), dpopJkt);
    }

    @Transactional
    public IssuedRefreshToken issuePasskey(User user,String jkt) {
        User current=lockUser(user.getId());
        if(current.getStatus()!=UserStatus.ACTIVE) throw new BusinessException(403,"Tài khoản không ở trạng thái hoạt động");
        Instant now=Instant.now();
        return createBounded(current,now,jkt,now.plusMillis(expirationMs),"PASSKEY");
    }

    @Transactional
    public IssuedRefreshToken replaceForDevice(User user, String jkt) {
        User current=lockUser(user.getId());
        if(current.getStatus()!=UserStatus.ACTIVE) throw new BusinessException(403,"Tài khoản không ở trạng thái hoạt động");
        Instant now=Instant.now();
        var previous=tokens.findAllByUserIdAndRevokedAtIsNull(user.getId()).stream()
            .filter(token->jkt.equals(token.getDpopJkt())).toList();
        previous.forEach(token->token.revoke(now,null));tokens.saveAll(previous);
        return createBounded(current,now,jkt,now.plusMillis(expirationMs),"BIOMETRIC");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public RotatedRefreshToken rotate(String rawToken, String dpopJkt) {
        Instant now = Instant.now();
        RefreshToken current = findForUpdate(rawToken)
                .orElseThrow(() -> invalidToken("Refresh token không hợp lệ"));

        requireBinding(current, dpopJkt);
        if (current.isRevoked()) {
            // Reuse revokes this client's sessions, never another device's binding.
            var compromised=tokens.findAllByUserIdAndRevokedAtIsNull(current.getUser().getId()).stream()
                    .filter(token->current.getDpopJkt().equals(token.getDpopJkt())).toList();
            compromised.forEach(token->token.revoke(now,null));tokens.saveAll(compromised);
            throw invalidToken("Refresh token đã bị thu hồi");
        }
        if (current.isExpired(now)) {
            current.revoke(now, null);
            tokens.save(current);
            throw invalidToken("Refresh token đã hết hạn");
        }
        if(com.careflow.identity.security.InteractiveSessionPolicy.expired(current,now)) {
            current.revoke(now,null);tokens.save(current);throw invalidToken("Phiên đã hết thời gian nhàn rỗi. Vui lòng xác thực lại.");
        }
        if (current.getUser().getStatus() != UserStatus.ACTIVE) {
            revokeActiveTokens(current.getUser(), now);
            throw new BusinessException(403, "Tài khoản không ở trạng thái hoạt động");
        }

        if(requireStaffPasskey && current.getUser().getRole()!=com.careflow.identity.domain.UserRole.PATIENT
                && !"PASSKEY".equals(current.getAuthenticationMethod())) {
            current.revoke(now,null);tokens.save(current);throw invalidToken("Tài khoản nội bộ cần đăng nhập lại bằng passkey.");
        }
        Instant absolute=current.getAbsoluteExpiresAt()==null?current.getExpiresAt():current.getAbsoluteExpiresAt();
        if(!absolute.isAfter(now)) throw invalidToken("Phiên đã hết thời hạn tuyệt đối");
        IssuedRefreshToken replacement = createBounded(current.getUser(),now,current.getDpopJkt(),absolute,current.getAuthenticationMethod(),current.getAuthenticatedAt(),current.getLastInteractiveAt());
        current.revoke(now, hash(replacement.value()));
        tokens.save(current);
        return new RotatedRefreshToken(
                current.getUser(), replacement.value(), replacement.expiresInSeconds(), replacement.sessionId());
    }

    @Transactional
    public void revoke(String rawToken, String dpopJkt) {
        Instant now = Instant.now();
        RefreshToken token = findForUpdate(rawToken).orElse(null);
        if (token != null) requireBinding(token, dpopJkt);
        while (token != null) {
            if (!token.isRevoked()) {
                token.revoke(now, null);
                tokens.save(token);
                break;
            }
            String replacement = token.getReplacedByTokenHash();
            token = replacement == null ? null : tokens.findByTokenHash(replacement).orElse(null);
        }
    }

    @Transactional
    public void revokeAll(User user) {
        User current=lockUser(user.getId());
        Instant now=Instant.now();
        revokeActiveTokens(current,now);
        devices.revokeAll(current.getId(),now);
    }

    private User lockUser(UUID userId) {
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> invalidToken("Tài khoản của phiên không tồn tại"));
    }

    private Optional<RefreshToken> findForUpdate(String rawToken) {
        String tokenHash = hash(rawToken);
        Optional<UUID> ownerId = tokens.findOwnerIdByTokenHash(tokenHash);
        if (ownerId.isEmpty()) return Optional.empty();
        // Every writer locks the account before the token, matching role/status changes.
        lockUser(ownerId.get());
        return tokens.findByTokenHash(tokenHash);
    }

    private IssuedRefreshToken create(User user, Instant now, String dpopJkt) {
        return createBounded(user,now,dpopJkt,now.plusMillis(expirationMs),"PASSWORD");
    }

    private IssuedRefreshToken createBounded(User user,Instant now,String dpopJkt,Instant absolute,String authenticationMethod) {
        return createBounded(user,now,dpopJkt,absolute,authenticationMethod,now,now);
    }
    private IssuedRefreshToken createBounded(User user,Instant now,String dpopJkt,Instant absolute,String authenticationMethod,Instant authenticatedAt,Instant lastInteractiveAt) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setDpopJkt(dpopJkt);
        token.setAuthenticationMethod(authenticationMethod);
        token.setAuthenticatedAt(authenticatedAt);
        token.setLastInteractiveAt(lastInteractiveAt);
        Instant expires=now.plusMillis(expirationMs);
        if(absolute.isBefore(expires)) expires=absolute;
        token.setAbsoluteExpiresAt(absolute);
        token.setExpiresAt(expires);
        tokens.save(token);
        return new IssuedRefreshToken(rawToken, java.time.Duration.between(now,expires).toSeconds(), token.getId());
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

    private void requireBinding(RefreshToken token, String jkt) {
        if (jkt == null || token.getDpopJkt() == null || !MessageDigest.isEqual(jkt.getBytes(StandardCharsets.UTF_8), token.getDpopJkt().getBytes(StandardCharsets.UTF_8))) {
            throw invalidToken("Refresh token không khớp khóa client");
        }
    }

    private BusinessException invalidToken(String message) {
        return new BusinessException(401, message);
    }

    public record IssuedRefreshToken(String value, long expiresInSeconds, UUID sessionId) {
    }

    public record RotatedRefreshToken(User user, String value, long expiresInSeconds, UUID sessionId) {
    }
}
