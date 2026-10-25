package com.careflow.identity.recovery;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.service.RefreshTokenService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryServiceTest {
    final PasswordResetCodeRepository codes=mock(PasswordResetCodeRepository.class);
    final UserRepository users=mock(UserRepository.class);
    final RefreshTokenService sessions=mock(RefreshTokenService.class);
    final RecoveryEmailSender mail=mock(RecoveryEmailSender.class);
    final ApplicationEventPublisher events=mock(ApplicationEventPublisher.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(4);
    final Instant now=Instant.parse("2026-10-18T08:00:00Z");
    final String email="patient@example.com", jkt="a".repeat(43);
    final PasswordResetCode challenge=new PasswordResetCode();
    final User user=new User();
    PasswordRecoveryService service;
    @BeforeEach void setup() {
        user.setId(UUID.randomUUID());user.setEmail(email);user.setStatus(UserStatus.ACTIVE);
        challenge.setEmailFingerprint(PasswordRecoveryService.fingerprint(email));
        challenge.setLastSentAt(now.minusSeconds(120));challenge.setWindowStartedAt(now);
        challenge.setChallengeId(UUID.randomUUID());challenge.setUserId(user.getId());
        challenge.setCodeHash(passwords.encode("123456"));challenge.setDpopJkt(jkt);
        challenge.setExpiresAt(now.plusSeconds(300));challenge.setDeliveryState("SENT");
        when(mail.configured()).thenReturn(true);
        when(codes.lockMailbox(challenge.getEmailFingerprint())).thenReturn(Optional.of(challenge));
        when(codes.lockChallenge(challenge.getChallengeId())).thenReturn(Optional.of(challenge));
        when(codes.findOwnerId(challenge.getChallengeId())).thenReturn(Optional.of(user.getId()));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        service=new PasswordRecoveryService(codes,users,passwords,sessions,mail,events,jdbc,Clock.fixed(now,ZoneOffset.UTC));
    }
    @Test void requestNormalizesEmailStoresHashAndNeverReturnsCode() {
        var result=service.request("  PATIENT@EXAMPLE.COM ",jkt);
        var event=org.mockito.ArgumentCaptor.forClass(PasswordRecoveryService.CodeRequested.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().code()).matches("[0-9]{6}");
        assertThat(passwords.matches(event.getValue().code(),challenge.getCodeHash())).isTrue();
        assertThat(challenge.getCodeHash()).isNotEqualTo(event.getValue().code());
        assertThat(event.getValue().deliver()).isTrue();
        assertThat(result.requestId()).isEqualTo(event.getValue().requestId());
        assertThat(result.expiresInSeconds()).isEqualTo(300);
        assertThat(result.message()).doesNotContain(event.getValue().code());
        assertThat(challenge.getSendCount()).isEqualTo(1);
        verify(mail,never()).sendCode(any(),any(),any()); // only after transaction commit
    }
    @Test void unknownMailboxGetsSamePublicResponseAndCooldownWithoutSending() {
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        var result=service.request(email,jkt);
        assertThat(challenge.getUserId()).isNull();
        var event=org.mockito.ArgumentCaptor.forClass(PasswordRecoveryService.CodeRequested.class);
        verify(events).publishEvent(event.capture());assertThat(event.getValue().deliver()).isFalse();
        assertThat(result.message()).startsWith("Nếu email đã đăng ký");
        assertStatus(429,()->service.request(email,jkt));
    }
    @Test void resendReplacesChallengeAndInvalidatesPreviousId() {
        var old=challenge.getChallengeId();challenge.setFailedAttempts(4);challenge.setUsedAt(now.minusSeconds(20));
        service.request(email,jkt);
        assertThat(challenge.getChallengeId()).isNotEqualTo(old);
        assertThat(challenge.getFailedAttempts()).isZero();assertThat(challenge.getUsedAt()).isNull();
        assertThat(challenge.getDeliveryState()).isEqualTo("PENDING");
    }
    @Test void cooldownAndHourlyQuotaApplyBeforeGeneratingCode() {
        challenge.setLastSentAt(now.minusSeconds(59));
        assertStatus(429,()->service.request(email,jkt));
        challenge.setLastSentAt(now.minusSeconds(61));challenge.setSendCount(5);
        assertStatus(429,()->service.request(email,jkt));verifyNoInteractions(events);
        challenge.setWindowStartedAt(now.minusSeconds(3600));service.request(email,jkt);
        assertThat(challenge.getSendCount()).isEqualTo(1);
    }
    @Test void missingProviderDoesNotPretendDelivery() {
        when(mail.configured()).thenReturn(false);assertStatus(503,()->service.request(email,jkt));
        verify(codes,never()).ensureMailbox(any());
    }
    @Test void wrongCodesAreCountedAndFifthGuessExhaustsChallenge() {
        var id=challenge.getChallengeId();
        for(int i=0;i<5;i++) assertStatus(400,()->service.verify(id,"000000",jkt));
        assertThat(challenge.getFailedAttempts()).isEqualTo(5);
        assertStatus(400,()->service.verify(id,"123456",jkt));verifyNoInteractions(sessions);
    }
    @ParameterizedTest @ValueSource(strings={"expired","used","different-device","exhausted","disabled","manual-lock","email-changed"})
    void unusableCodesNeverIssueResetPermission(String scenario) {
        String device=jkt;
        switch(scenario) {
            case "expired" -> challenge.setExpiresAt(now);
            case "used" -> challenge.setUsedAt(now.minusSeconds(1));
            case "different-device" -> device="b".repeat(43);
            case "exhausted" -> challenge.setFailedAttempts(5);
            case "disabled" -> user.setStatus(UserStatus.DISABLED);
            case "manual-lock" -> user.setStatus(UserStatus.LOCKED);
            case "email-changed" -> user.setEmail("changed@example.com");
        }
        final String proof=device;
        assertStatus(400,()->service.verify(challenge.getChallengeId(),"123456",proof));
        assertThat(challenge.getResetTokenHash()).isNull();verifyNoInteractions(sessions);
    }
    @Test void pendingOrFailedDeliveryCannotVerifyCode() {
        for(String state:List.of("PENDING","FAILED")) {
            challenge.setDeliveryState(state);
            assertStatus(503,()->service.verify(challenge.getChallengeId(),"123456",jkt));
        }
        verifyNoInteractions(sessions);
    }
    @Test void verifyingCodeOnlyIssuesHashedShortLivedPermissionAndConsumesOtp() {
        var grant=service.verify(challenge.getChallengeId(),"123456",jkt);
        assertThat(grant.resetToken()).matches("[A-Za-z0-9_-]{43}");assertThat(grant.expiresInSeconds()).isEqualTo(300);
        assertThat(challenge.getResetTokenHash()).isEqualTo(PasswordRecoveryService.fingerprint(grant.resetToken()));
        assertThat(challenge.getResetTokenHash()).isNotEqualTo(grant.resetToken());assertThat(challenge.getCodeHash()).isNull();
        assertThat(challenge.getVerifiedAt()).isEqualTo(now);assertThat(challenge.getUsedAt()).isNull();
        assertThat(user.getPasswordHash()).isNull();verifyNoInteractions(sessions);
        verify(users,never()).saveAndFlush(any());verifyNoInteractions(events);
        assertStatus(400,()->service.verify(challenge.getChallengeId(),"123456",jkt));
    }
    @Test void successfulResetRevokesAllSessionsConsumesPermissionAndNotifiesWithoutAutoLogin() {
        user.setStatus(UserStatus.LOCKED);user.setLockedUntil(now.plusSeconds(300));user.setFailedLoginAttempts(4);
        var grant=service.verify(challenge.getChallengeId(),"123456",jkt);
        service.confirm(grant.requestId(),grant.resetToken(),"NewPassword123@",jkt);
        assertThat(passwords.matches("NewPassword123@",user.getPasswordHash())).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();assertThat(challenge.getUsedAt()).isEqualTo(now);
        assertThat(challenge.getResetTokenHash()).isNull();
        verify(sessions).revokeAll(user);verify(codes).invalidateUser(user.getId(),now);
        verify(events).publishEvent(new PasswordRecoveryService.PasswordChanged(email,challenge.getChallengeId()));
        assertStatus(400,()->service.confirm(grant.requestId(),grant.resetToken(),"AnotherPassword123@",jkt));
        verify(sessions,times(1)).revokeAll(user);
    }
    @ParameterizedTest @ValueSource(strings={"unverified","wrong-token","expired","used","different-device","disabled","email-changed"})
    void passwordResetRequiresFreshVerifiedPermission(String scenario) {
        String token="x".repeat(43),device=jkt;
        if(!scenario.equals("unverified")) token=service.verify(challenge.getChallengeId(),"123456",jkt).resetToken();
        switch(scenario) {
            case "wrong-token" -> token="wrong".repeat(9);
            case "expired" -> challenge.setResetExpiresAt(now);
            case "used" -> challenge.setUsedAt(now);
            case "different-device" -> device="b".repeat(43);
            case "disabled" -> user.setStatus(UserStatus.DISABLED);
            case "email-changed" -> user.setEmail("changed@example.com");
        }
        final String permission=token,proof=device;
        assertStatus(400,()->service.confirm(challenge.getChallengeId(),permission,"NewPassword123@",proof));
        assertThat(user.getPasswordHash()).isNull();verifyNoInteractions(sessions);
    }
    @Test void resendAlsoInvalidatesAnyPreviouslyVerifiedPermission() {
        var grant=service.verify(challenge.getChallengeId(),"123456",jkt);
        service.request(email,jkt);
        assertThat(challenge.getVerifiedAt()).isNull();assertThat(challenge.getResetTokenHash()).isNull();
        assertStatus(400,()->service.confirm(grant.requestId(),grant.resetToken(),"NewPassword123@",jkt));
    }
    @Test void unknownMailboxStillVerifiesAndCountsWrongCodeWithoutChangingAnyUser() {
        when(codes.findOwnerId(challenge.getChallengeId())).thenReturn(Optional.empty());
        assertStatus(400,()->service.verify(challenge.getChallengeId(),"000000",jkt));
        assertThat(challenge.getFailedAttempts()).isEqualTo(1);verify(users,never()).findByIdForUpdate(any());verifyNoInteractions(sessions);
    }
    @Test void missingDeviceProofAndOversizeUtf8PasswordsAreRejected() {
        assertStatus(401,()->service.request(email,null));
        assertStatus(400,()->service.confirm(challenge.getChallengeId(),"x".repeat(43),"Aa1@"+"ấ".repeat(30),jkt));
    }
    static void assertStatus(int status,Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(status));
    }
}
