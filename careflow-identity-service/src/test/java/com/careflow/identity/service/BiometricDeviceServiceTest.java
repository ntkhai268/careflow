package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.repository.*;
import com.careflow.identity.security.JwtService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiometricDeviceServiceTest {
    final BiometricDeviceRepository devices=mock(BiometricDeviceRepository.class);
    final UserRepository users=mock(UserRepository.class);
    final RefreshTokenService sessions=mock(RefreshTokenService.class);
    final JwtService jwt=mock(JwtService.class);
    final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(4);
    final Instant now=Instant.parse("2026-10-18T15:00:00Z");
    final String token="s".repeat(43),jkt="k".repeat(43);
    final User user=new User();final BiometricDevice device=new BiometricDevice();
    BiometricDeviceService service;
    @BeforeEach void setup() {
        user.setId(UUID.randomUUID());user.setUsername("patient");user.setEmail("patient@example.test");
        user.setStatus(UserStatus.ACTIVE);user.setRole(UserRole.PATIENT);user.setPasswordHash(passwords.encode("Password123!"));
        device.setId(UUID.randomUUID());device.setUserId(user.getId());device.setDpopJkt(jkt);
        device.setExpiresAt(now.plusSeconds(3600));device.setSecretHash(BiometricDeviceService.hash(token));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(devices.owner(device.getId())).thenReturn(Optional.of(user.getId()));when(devices.lock(device.getId())).thenReturn(Optional.of(device));
        service=new BiometricDeviceService(devices,users,passwords,sessions,jwt,Clock.fixed(now,ZoneOffset.UTC));
    }
    @Test void enrollmentRequiresCurrentPasswordAndStoresOnlyRandomSecretHash() {
        var result=service.enroll(user.getId(),"Password123!",jkt);
        var stored=org.mockito.ArgumentCaptor.forClass(BiometricDevice.class);verify(devices).saveAndFlush(stored.capture());
        assertThat(result.deviceToken()).matches("[A-Za-z0-9_-]{43}");assertThat(stored.getValue().getSecretHash()).isEqualTo(BiometricDeviceService.hash(result.deviceToken()));
        assertThat(stored.getValue().getSecretHash()).doesNotContain(result.deviceToken());
        assertThat(result.expiresAt()).isEqualTo(now.plus(Duration.ofDays(90)));verify(devices).revokeDevice(user.getId(),jkt,now);
        verifyNoInteractions(sessions,jwt);
    }
    @Test void wrongEnrollmentPasswordCountsAndFifthLocksAndRevokes() {
        for(int i=0;i<5;i++) assertThatThrownBy(()->service.enroll(user.getId(),"WrongPassword1!",jkt)).isInstanceOf(BusinessException.class);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);verify(sessions,never()).revokeAll(user);verify(devices,never()).saveAndFlush(any());
    }
    @ParameterizedTest @ValueSource(strings={"revoked","expired","wrong-secret","wrong-key","locked","disabled","wrong-role"})
    void invalidDeviceCannotCreateAnySession(String scenario) {
        String secret=token,key=jkt;
        switch(scenario) {
            case "revoked" -> device.setRevokedAt(now);
            case "expired" -> device.setExpiresAt(now);
            case "wrong-secret" -> secret="x".repeat(43);
            case "wrong-key" -> key="x".repeat(43);
            case "locked" -> user.setStatus(UserStatus.LOCKED);
            case "disabled" -> user.setStatus(UserStatus.DISABLED);
            case "wrong-role" -> user.setRole(UserRole.ADMIN);
        }
        final String supplied=secret,proof=key;
        assertThatThrownBy(()->service.login(device.getId(),supplied,proof)).isInstanceOf(BusinessException.class);verifyNoInteractions(sessions,jwt);
    }
    @Test void validDeviceCreatesFreshSessionRatherThanResurrectingLoggedOutTokens() {
        UUID sessionId=UUID.randomUUID();when(sessions.replaceForDevice(user,jkt)).thenReturn(new RefreshTokenService.IssuedRefreshToken("fresh-refresh",3600,sessionId));
        when(jwt.issue(user,sessionId)).thenReturn("fresh-access");
        var result=service.login(device.getId(),token,jkt);
        assertThat(result.accessToken()).isEqualTo("fresh-access");assertThat(result.refreshToken()).isEqualTo("fresh-refresh");
        assertThat(device.getLastUsedAt()).isEqualTo(now);verify(sessions).replaceForDevice(user,jkt);
    }
    @Test void disableRevokesRegistrationAndKeepsExistingSession() {
        service.disable(user.getId(),device.getId(),jkt);assertThat(device.getRevokedAt()).isEqualTo(now);verifyNoInteractions(sessions);
    }
    @Test void otherAccountOrDeviceCannotDisableRegistration() {
        device.setUserId(UUID.randomUUID());assertThatThrownBy(()->service.disable(user.getId(),device.getId(),jkt)).isInstanceOf(BusinessException.class);
        assertThat(device.getRevokedAt()).isNull();
    }
    @Test void logoutAllRevokesDeviceWhileSingleLogoutDoesNot() {
        var tokens=mock(RefreshTokenRepository.class);var service=new RefreshTokenService(tokens,users,devices,3600000);
        when(tokens.findAllByUserIdAndRevokedAtIsNull(user.getId())).thenReturn(List.of());
        service.revokeAll(user);verify(devices).revokeAll(eq(user.getId()),any());
    }
}
