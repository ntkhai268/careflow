package com.careflow.identity.recovery;

import com.careflow.identity.domain.*;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;

class RecoveryCredentialRevocationTest {
    @Test void successfulRecoveryRemovesEveryReauthenticationCredentialAtomically() {
        var codes=mock(PasswordResetCodeRepository.class);var users=mock(UserRepository.class);
        var passwords=mock(PasswordEncoder.class);var sessions=mock(RefreshTokenService.class);
        var mail=mock(RecoveryEmailSender.class);var events=mock(ApplicationEventPublisher.class);var jdbc=mock(JdbcTemplate.class);
        Instant now=Instant.now();UUID id=UUID.randomUUID();User user=new User();user.setId(UUID.randomUUID());user.setEmail("test@example.com");user.setStatus(UserStatus.ACTIVE);
        PasswordResetCode challenge=new PasswordResetCode();challenge.setChallengeId(id);challenge.setEmailFingerprint(PasswordRecoveryService.fingerprint(user.getEmail()));
        challenge.setVerifiedAt(now);challenge.setResetExpiresAt(now.plusSeconds(300));challenge.setDpopJkt("key");challenge.setResetTokenHash(PasswordRecoveryService.fingerprint("grant"));
        when(codes.findOwnerId(id)).thenReturn(Optional.of(user.getId()));when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));when(codes.lockChallenge(id)).thenReturn(Optional.of(challenge));
        var service=new PasswordRecoveryService(codes,users,passwords,sessions,mail,events,jdbc,Clock.fixed(now,ZoneOffset.UTC));
        service.confirm(id,"grant","a-long-enough-password","key");
        verify(sessions).revokeAll(user);
        verify(jdbc).update("DELETE FROM identity.login_questions WHERE user_id=?",user.getId());
        verify(jdbc).update("DELETE FROM identity.passkeys WHERE user_id=?",user.getId());
        verify(jdbc).update("DELETE FROM identity.login_challenges WHERE user_id=?",user.getId());
    }
}
