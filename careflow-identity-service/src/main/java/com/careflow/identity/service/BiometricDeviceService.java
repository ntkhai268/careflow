package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.BiometricDevice;
import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserRole;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.dto.LoginResponse;
import com.careflow.identity.dto.UserResponse;
import com.careflow.identity.repository.BiometricDeviceRepository;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class BiometricDeviceService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofSeconds(900);
    private static final Duration DEVICE_EXPIRATION = Duration.ofDays(90);

    private final BiometricDeviceRepository devices;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final RefreshTokenService sessions;
    private final JwtService jwt;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public BiometricDeviceService(BiometricDeviceRepository devices, UserRepository users, PasswordEncoder passwords,
                                  RefreshTokenService sessions, JwtService jwt, Clock clock) {
        this.devices = devices;
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.jwt = jwt;
        this.clock = clock;
    }

    public record Registration(UUID credentialId, String deviceToken, Instant expiresAt) {
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public Registration enroll(UUID userId, String password, String jkt) {
        proof(jkt);
        User user = users.findByIdForUpdate(userId).orElseThrow(BiometricDeviceService::invalid);
        requireActivePatient(user);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72 || !passwords.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(clock.instant(), MAX_FAILED_ATTEMPTS, LOCK_DURATION);
            users.save(user);
            throw new BusinessException(401, "Mật khẩu hiện tại không đúng.");
        }
        user.setFailedLoginAttempts(0);
        users.saveAndFlush(user);

        Instant now = clock.instant();
        devices.revokeDevice(userId, jkt, now);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        BiometricDevice device = new BiometricDevice();
        device.setId(UUID.randomUUID());
        device.setUserId(userId);
        device.setSecretHash(hash(token));
        device.setDpopJkt(jkt);
        device.setCreatedAt(now);
        device.setExpiresAt(now.plus(DEVICE_EXPIRATION));
        devices.saveAndFlush(device);
        return new Registration(device.getId(), token, device.getExpiresAt());
    }

    @Transactional
    public LoginResponse login(UUID id, String token, String jkt) {
        proof(jkt);
        UUID owner = devices.owner(id).orElseThrow(BiometricDeviceService::invalid);
        User user = users.findByIdForUpdate(owner).orElseThrow(BiometricDeviceService::invalid);
        BiometricDevice device = devices.lock(id).orElseThrow(BiometricDeviceService::invalid);
        Instant now = clock.instant();
        if (device.getRevokedAt() != null
                || !device.getExpiresAt().isAfter(now)
                || !jkt.equals(device.getDpopJkt())
                || token == null
                || !MessageDigest.isEqual(
                        hash(token).getBytes(StandardCharsets.US_ASCII),
                        device.getSecretHash().getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
        // A valid enrolled device can recover a temporary password-guess lock.
        if (user.getStatus() == UserStatus.LOCKED && user.getLockedUntil() != null) {
            user.setStatus(UserStatus.ACTIVE);
            user.setLockedUntil(null);
        }
        requireActivePatient(user);
        device.setLastUsedAt(now);
        devices.save(device);
        user.recordSuccessfulLogin(now);
        users.saveAndFlush(user);
        var session = sessions.replaceForDevice(user, jkt);
        return new LoginResponse(
                jwt.issue(user, session.sessionId()),
                session.value(),
                "Bearer",
                jwt.expirationSeconds(),
                session.expiresInSeconds(),
                UserResponse.from(user));
    }

    @Transactional
    public void disable(UUID userId, UUID credentialId, String jkt) {
        proof(jkt);
        users.findByIdForUpdate(userId).orElseThrow(BiometricDeviceService::invalid);
        var device = devices.lock(credentialId).orElseThrow(BiometricDeviceService::invalid);
        if (!userId.equals(device.getUserId()) || !jkt.equals(device.getDpopJkt())) {
            throw invalid();
        }
        device.setRevokedAt(clock.instant());
        devices.save(device);
    }

    private static void requireActivePatient(User user) {
        if (user.getStatus() != UserStatus.ACTIVE || user.getRole() != UserRole.PATIENT) {
            throw new BusinessException(403, "Tài khoản không thể đăng nhập sinh trắc học. Vui lòng đăng nhập bằng mật khẩu.");
        }
    }

    private static void proof(String jkt) {
        if (jkt == null || jkt.isBlank()) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(401, "Thiết lập sinh trắc học đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập bằng mật khẩu và bật lại.");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
