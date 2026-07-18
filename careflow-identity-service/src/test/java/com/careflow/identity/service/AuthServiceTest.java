package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.dto.*;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwordEncoder, jwtService);
    }

    @Test
    void publicRegistrationAlwaysCreatesNormalizedPatient() {
        when(passwordEncoder.encode("StrongPassword1!")).thenReturn("hash");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        UserResponse response = service.register(new RegisterRequest(
                "NguyenVanA", "VANA@EXAMPLE.COM", "StrongPassword1!"));

        assertThat(response.role()).isEqualTo(UserRole.PATIENT);
        assertThat(response.username()).isEqualTo("nguyenvana");
        assertThat(response.email()).isEqualTo("vana@example.com");
    }

    @Test
    void duplicateRegistrationReturnsConflict() {
        when(users.existsByUsernameIgnoreCase("nguyenvana")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "NguyenVanA", "vana@example.com", "StrongPassword1!")))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(409);
    }

    @Test
    void fifthWrongPasswordTemporarilyLocksAccount() {
        User user = activeUser();
        user.setFailedLoginAttempts(4);
        when(users.findForLogin("patient")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("patient", "wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(401);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isNotNull();
        verify(users).save(user);
    }

    @Test
    void successfulLoginResetsFailuresAndReturnsJwt() {
        User user = activeUser();
        user.setFailedLoginAttempts(3);
        when(users.findForLogin("patient")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        when(jwtService.issue(user)).thenReturn("jwt");
        when(jwtService.expirationSeconds()).thenReturn(86400L);

        LoginResponse response = service.login(new LoginRequest("patient", "correct"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("patient");
        user.setEmail("patient@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.PATIENT);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
