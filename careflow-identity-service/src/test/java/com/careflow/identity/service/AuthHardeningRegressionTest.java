package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.dto.LoginRequest;
import com.careflow.identity.repository.*;
import com.careflow.identity.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthHardeningRegressionTest {
    final UserRepository users=mock(UserRepository.class);
    final PasswordEncoder passwords=mock(PasswordEncoder.class);
    final RefreshTokenService sessions=mock(RefreshTokenService.class);
    final JwtService jwt=mock(JwtService.class);
    @Test void unknownUserStillPerformsPasswordComparison() {
        when(passwords.encode(any())).thenReturn("dummy");
        var auth=new AuthService(users,passwords,jwt,sessions);
        assertThatThrownBy(()->auth.login(new LoginRequest("unknown","guess"),"key")).isInstanceOf(BusinessException.class);
        verify(passwords).matches("guess","dummy");verifyNoInteractions(sessions);
    }
    @Test void productionStaffPasswordNeverCreatesFullSession() {
        User user=user(UserRole.DOCTOR);
        when(users.findByUsernameIgnoreCaseOrEmailIgnoreCase("doctor","doctor")).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(passwords.matches("correct","hash")).thenReturn(true);
        var auth=new AuthService(users,passwords,jwt,sessions);
        ReflectionTestUtils.setField(auth,"requireStaffPasskey",true);
        assertThatThrownBy(()->auth.login(new LoginRequest("doctor","correct"),"key"))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));
        verifyNoInteractions(sessions,jwt);
    }
    @Test void wrongRemotePasswordDoesNotRevokeTrustedDevices() {
        User user=user(UserRole.PATIENT);user.setFailedLoginAttempts(4);
        when(users.findByUsernameIgnoreCaseOrEmailIgnoreCase("patient","patient")).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        assertThatThrownBy(()->new AuthService(users,passwords,jwt,sessions).login(new LoginRequest("patient","wrong"),"key"))
                .isInstanceOf(BusinessException.class);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);verifyNoInteractions(sessions);
    }
    @Test void retiredQuestionsCannotLoginResetOrEnroll() {
        var jdbc=mock(JdbcTemplate.class);
        var service=new LoginMethodsService(jdbc,users,passwords,sessions,jwt,"localhost","http://localhost:3000");
        assertThatThrownBy(()->service.questionLogin("patient","memory","answer","key"))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(410));
        assertThatThrownBy(()->service.questionReset("patient","memory","answer","new-password"))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(410));
        assertThatThrownBy(()->service.saveQuestion(UUID.randomUUID(),"password","memory","answer"))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(410));
        assertThatThrownBy(()->service.assertion(UUID.randomUUID(),"credential","key","new-password"))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(410));
        verifyNoInteractions(jdbc,sessions,jwt,users);
    }
    @Test void refreshRotationCannotExtendOriginalAbsoluteDeadline() {
        var tokens=mock(RefreshTokenRepository.class);var devices=mock(BiometricDeviceRepository.class);
        User user=user(UserRole.PATIENT);RefreshToken old=new RefreshToken();old.setId(UUID.randomUUID());old.setUser(user);
        old.setDpopJkt("key");old.setExpiresAt(Instant.now().plusSeconds(120));old.setAbsoluteExpiresAt(old.getExpiresAt());
        when(tokens.findOwnerIdByTokenHash(any())).thenReturn(Optional.of(user.getId()));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));when(tokens.findByTokenHash(any())).thenReturn(Optional.of(old));
        new RefreshTokenService(tokens,users,devices,2592000000L).rotate("old","key");
        var saved=org.mockito.ArgumentCaptor.forClass(RefreshToken.class);verify(tokens,times(2)).save(saved.capture());
        var fresh=saved.getAllValues().stream().filter(t->t!=old).findFirst().orElseThrow();
        assertThat(fresh.getExpiresAt()).isEqualTo(old.getAbsoluteExpiresAt());
        assertThat(fresh.getAbsoluteExpiresAt()).isEqualTo(old.getAbsoluteExpiresAt());
    }
    @Test void refreshCannotPromoteLegacyStaffPasswordSession() {
        var tokens=mock(RefreshTokenRepository.class);var devices=mock(BiometricDeviceRepository.class);
        User user=user(UserRole.DOCTOR);RefreshToken old=new RefreshToken();old.setId(UUID.randomUUID());old.setUser(user);
        old.setDpopJkt("key");old.setExpiresAt(Instant.now().plusSeconds(120));
        when(tokens.findOwnerIdByTokenHash(any())).thenReturn(Optional.of(user.getId()));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));when(tokens.findByTokenHash(any())).thenReturn(Optional.of(old));
        var service=new RefreshTokenService(tokens,users,devices,2592000000L);
        ReflectionTestUtils.setField(service,"requireStaffPasskey",true);
        assertThatThrownBy(()->service.rotate("legacy","key")).isInstanceOf(BusinessException.class);
        assertThat(old.isRevoked()).isTrue();verify(tokens,times(1)).save(old);
    }
    private User user(UserRole role) {
        User user=new User();user.setId(UUID.randomUUID());user.setRole(role);user.setStatus(UserStatus.ACTIVE);user.setPasswordHash("hash");return user;
    }
}
