package com.careflow.identity.service;

import com.careflow.identity.domain.*;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountSessionRevocationTest {
    private User user;
    private UserRepository users;
    private RefreshTokenService refresh;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(UserRole.DOCTOR);
        user.setStatus(UserStatus.ACTIVE);
        users = mock(UserRepository.class);
        refresh = mock(RefreshTokenService.class);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        auth = new AuthService(users, mock(PasswordEncoder.class), mock(JwtService.class), refresh);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "DISABLED"})
    void inactiveStatusRevokesAllSessions(String state) {
        auth.updateStatus(user.getId(), UserStatus.valueOf(state));
        verify(refresh).revokeAll(user);
    }

    @Test
    void manualLockDoesNotRetainTimedAutoUnlock() {
        user.setLockedUntil(java.time.Instant.now().plusSeconds(30));
        auth.updateStatus(user.getId(), UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void roleChangeRevokesPriorPrivilege() {
        auth.updateRole(user.getId(), UserRole.PATIENT);
        verify(refresh).revokeAll(user);
        assertThat(user.getRole()).isEqualTo(UserRole.PATIENT);
    }

    @Test
    void reactivationCannotResurrectOldSession() {
        user.setStatus(UserStatus.DISABLED);
        auth.updateStatus(user.getId(), UserStatus.ACTIVE);
        verify(refresh).revokeAll(user);
    }

    @Test
    void unchangedRoleDoesNotInvalidateDevices() {
        auth.updateRole(user.getId(), UserRole.DOCTOR);
        verify(refresh, never()).revokeAll(any());
    }

    @Test
    void logoutAllUsesOnlyTheAuthenticatedAccount() {
        auth.logoutAll(user.getId());
        verify(refresh).revokeAll(user);
    }
}
