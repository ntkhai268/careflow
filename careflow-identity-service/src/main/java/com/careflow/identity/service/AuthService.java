package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.identity.domain.User;
import com.careflow.identity.domain.UserRole;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.dto.*;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
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

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username) || users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(409, "Username hoặc email đã tồn tại");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.PATIENT);
        try {
            return UserResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(409, "Username hoặc email đã tồn tại");
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request) {
        String login = request.usernameOrEmail().trim().toLowerCase(Locale.ROOT);
        User user = users.findByUsernameIgnoreCaseOrEmailIgnoreCase(login, login)
                .orElseThrow(() -> new BusinessException(401, "Thông tin đăng nhập không hợp lệ"));

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
            }
            users.save(user);
            throw new BusinessException(401, "Thông tin đăng nhập không hợp lệ");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        users.save(user);
        return new LoginResponse(jwtService.issue(user), "Bearer", jwtService.expirationSeconds(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return UserResponse.from(requireUser(userId));
    }

    @Transactional
    public UserResponse updateStatus(UUID userId, UserStatus status) {
        User user = requireUser(userId);
        user.setStatus(status);
        if (status != UserStatus.LOCKED) user.setLockedUntil(null);
        if (status == UserStatus.ACTIVE) user.setFailedLoginAttempts(0);
        return UserResponse.from(users.save(user));
    }

    private User requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }
}
