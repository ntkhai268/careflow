package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserRole;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.dto.*;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final String dummyHash;
    @org.springframework.beans.factory.annotation.Value("${security.auth.require-staff-passkey:false}")
    private boolean requireStaffPasskey;
    @org.springframework.beans.factory.annotation.Value("${security.auth.public-registration-enabled:true}")
    private boolean publicRegistrationEnabled=true;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if(!publicRegistrationEnabled) throw new BusinessException(503,"Đăng ký trực tuyến chưa được mở. Vui lòng liên hệ cơ sở y tế để xác minh tài khoản.");
        throw new BusinessException(410,"Vui lòng cập nhật ứng dụng và xác minh email để đăng ký tài khoản.");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request, String dpopJkt) {
        String login = request.usernameOrEmail().trim().toLowerCase(Locale.ROOT);
        User user = users.findByUsernameIgnoreCaseOrEmailIgnoreCase(login, login)
                .orElseGet(() -> {
                    passwordEncoder.matches(request.password(), dummyHash);
                    throw new BusinessException(401, "Thông tin đăng nhập không hợp lệ");
                });

        user = requireUserForUpdate(user.getId());
        Instant now = Instant.now();
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(403, "Tài khoản đã bị vô hiệu hóa");
        }
        if (user.getStatus() == UserStatus.LOCKED && user.getLockedUntil() != null
                && !user.getLockedUntil().isAfter(now)) {
            user.setStatus(UserStatus.ACTIVE);
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(403, "Tài khoản đang bị khóa");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int failures = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failures);
            if (failures >= MAX_FAILED_ATTEMPTS) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(now.plus(LOCK_DURATION));
                // Untrusted failed attempts must not permanently revoke trusted devices.
            }
            users.save(user);
            throw new BusinessException(401, "Thông tin đăng nhập không hợp lệ");
        }

        if(user.getRole()!=UserRole.PATIENT && (requireStaffPasskey || users.isProvisionedStaff(user.getId()))) {
            throw new BusinessException(403,"Tài khoản nội bộ phải đăng nhập bằng passkey đã đăng ký.");
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        users.save(user);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokens.issue(user, dpopJkt);
        return tokenResponse(user, refreshToken.value(), refreshToken.expiresInSeconds(), refreshToken.sessionId());
    }

    public LoginResponse refresh(RefreshTokenRequest request, String dpopJkt) {
        RefreshTokenService.RotatedRefreshToken refreshToken = refreshTokens.rotate(request.refreshToken(), dpopJkt);
        return tokenResponse(refreshToken.user(), refreshToken.value(), refreshToken.expiresInSeconds(), refreshToken.sessionId());
    }

    public void logout(RefreshTokenRequest request, String dpopJkt) {
        refreshTokens.revoke(request.refreshToken(), dpopJkt);
    }

    @Transactional
    public void logoutAll(UUID userId) {
        refreshTokens.revokeAll(requireUserForUpdate(userId));
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return UserResponse.from(requireUser(userId));
    }

    @Transactional
    public UserResponse updateStatus(UUID userId, UserStatus status) {
        User user = requireUserForUpdate(userId);
        boolean changed = user.getStatus() != status;
        user.setStatus(status);
        user.setLockedUntil(null);
        if (status == UserStatus.ACTIVE) user.setFailedLoginAttempts(0);
        if (changed) {
            refreshTokens.revokeAll(user);
            users.invalidateStaffPasskeyGrants(userId);
        }
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse updateRole(UUID userId, com.careflow.identity.domain.UserRole role) {
        User user = requireUserForUpdate(userId);
        boolean changed = user.getRole() != role;
        boolean staffPromotion = user.getRole() == UserRole.PATIENT && role != UserRole.PATIENT;
        user.setRole(role);
        if (changed) {
            refreshTokens.revokeAll(user);
            users.invalidateStaffPasskeyGrants(userId);
            // Patient keys could be enrolled using only a password; staff requires trusted issuance.
            if (staffPromotion) users.retirePatientPasskeysForStaffPromotion(userId);
        }
        return UserResponse.from(users.save(user));
    }

    private User requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private User requireUserForUpdate(UUID id) {
        return users.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private LoginResponse tokenResponse(User user, String refreshToken, long refreshExpiresInSeconds, UUID sessionId) {
        return new LoginResponse(
                jwtService.issue(user, sessionId),
                refreshToken,
                "Bearer",
                jwtService.expirationSeconds(),
                refreshExpiresInSeconds,
                UserResponse.from(user));
    }
}
