package com.careflow.identity.recovery;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.service.RefreshTokenService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class PasswordRecoveryService {
    private final PasswordResetCodeRepository codes;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final RefreshTokenService sessions;
    private final RecoveryEmailSender sender;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();

    public PasswordRecoveryService(PasswordResetCodeRepository codes,UserRepository users,
            PasswordEncoder passwords,RefreshTokenService sessions,RecoveryEmailSender sender,
            ApplicationEventPublisher events,JdbcTemplate jdbc,Clock clock) {
        this.codes=codes;this.users=users;this.passwords=passwords;this.sessions=sessions;
        this.sender=sender;this.events=events;this.jdbc=jdbc;this.clock=clock;
    }

    public record CodeRequested(String email,String code,UUID requestId,boolean deliver) {}
    public record PasswordChanged(String email,UUID requestId) {}
    public record VerifyResult(UUID requestId,String resetToken,int expiresInSeconds) {}
    public record RequestResult(UUID requestId,int expiresInSeconds,int resendAfterSeconds,String message) {}

    @Transactional(noRollbackFor=BusinessException.class)
    public RequestResult request(String email,String jkt) {
        requireProof(jkt);
        if (!sender.configured()) throw new BusinessException(503,"Dịch vụ gửi email chưa sẵn sàng. Vui lòng thử lại sau.");
        String normalized=email.strip().toLowerCase(Locale.ROOT);
        String fingerprint=fingerprint(normalized);
        codes.ensureMailbox(fingerprint);
        var challenge=codes.lockMailbox(fingerprint).orElseThrow();
        Instant now=clock.instant();
        if(challenge.getChallengeId()!=null && challenge.getLastSentAt().plusSeconds(60).isAfter(now))
            throw new BusinessException(429,"Vui lòng chờ 60 giây trước khi gửi lại mã.");
        if(!challenge.getWindowStartedAt().plusSeconds(3600).isAfter(now)) {
            challenge.setWindowStartedAt(now);challenge.setSendCount(0);
        }
        if(challenge.getSendCount()>=5) throw new BusinessException(429,"Đã đạt giới hạn gửi mã. Vui lòng thử lại sau.");
        // Same expensive hash and database path for registered and unknown emails.
        String code=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
        String hash=passwords.encode(code);
        var user=users.findByEmailIgnoreCase(normalized).filter(PasswordRecoveryService::eligible).orElse(null);
        UUID id=UUID.randomUUID();
        challenge.setChallengeId(id);challenge.setUserId(user==null?null:user.getId());
        challenge.setCodeHash(hash);challenge.setDpopJkt(jkt);challenge.setExpiresAt(now.plusSeconds(300));
        challenge.setUsedAt(null);challenge.setVerifiedAt(null);challenge.setResetTokenHash(null);challenge.setResetExpiresAt(null);
        challenge.setFailedAttempts(0);challenge.setLastSentAt(now);
        challenge.setSendCount(challenge.getSendCount()+1);challenge.setDeliveryState("PENDING");
        codes.save(challenge);
        events.publishEvent(new CodeRequested(normalized,code,id,user!=null));
        return new RequestResult(id,300,60,"Nếu email đã đăng ký và có thể khôi phục, mã xác nhận sẽ được gửi tới hộp thư của bạn.");
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public VerifyResult verify(UUID id,String code,String jkt) {
        requireProof(jkt);
        UUID owner=codes.findOwnerId(id).orElse(null);
        User user=owner==null?null:users.findByIdForUpdate(owner).orElse(null);
        var challenge=codes.lockChallenge(id).orElseThrow(PasswordRecoveryService::invalid);
        Instant now=clock.instant();
        if(challenge.getUsedAt()!=null || challenge.getVerifiedAt()!=null || challenge.getCodeHash()==null
                || challenge.getExpiresAt()==null || !challenge.getExpiresAt().isAfter(now)
                || challenge.getFailedAttempts()>=5 || !jkt.equals(challenge.getDpopJkt())) throw invalid();
        // Unknown mailboxes also perform BCrypt verification and count guesses.
        if(!passwords.matches(code,challenge.getCodeHash())) {
            challenge.setFailedAttempts(challenge.getFailedAttempts()+1);codes.save(challenge);throw invalid();
        }
        if(user==null || !eligible(user) || !fingerprint(user.getEmail().strip().toLowerCase(Locale.ROOT)).equals(challenge.getEmailFingerprint())) throw invalid();
        if(!"SENT".equals(challenge.getDeliveryState())) throw new BusinessException(503,"Chưa xác nhận được email gửi mã. Vui lòng thử gửi lại mã.");
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        challenge.setVerifiedAt(now);challenge.setCodeHash(null);
        challenge.setResetTokenHash(fingerprint(token));challenge.setResetExpiresAt(now.plusSeconds(300));
        codes.saveAndFlush(challenge);
        return new VerifyResult(id,token,300);
    }

    @Transactional
    public void confirm(UUID id,String resetToken,String newPassword,String jkt) {
        requireProof(jkt);
        if(newPassword.getBytes(StandardCharsets.UTF_8).length>72)
            throw new BusinessException(400,"Mật khẩu mới tối đa 72 byte UTF-8.");
        // All session writers lock the account first, then the challenge/token.
        UUID owner=codes.findOwnerId(id).orElseThrow(PasswordRecoveryService::invalidReset);
        User user=users.findByIdForUpdate(owner).orElseThrow(PasswordRecoveryService::invalidReset);
        var challenge=codes.lockChallenge(id).orElseThrow(PasswordRecoveryService::invalidReset);
        Instant now=clock.instant();
        if(challenge.getUsedAt()!=null || challenge.getVerifiedAt()==null || challenge.getResetTokenHash()==null
                || challenge.getResetExpiresAt()==null || !challenge.getResetExpiresAt().isAfter(now)
                || !jkt.equals(challenge.getDpopJkt()) || resetToken==null
                || !MessageDigest.isEqual(fingerprint(resetToken).getBytes(StandardCharsets.US_ASCII),challenge.getResetTokenHash().getBytes(StandardCharsets.US_ASCII))
                || !eligible(user) || !fingerprint(user.getEmail().strip().toLowerCase(Locale.ROOT)).equals(challenge.getEmailFingerprint())) throw invalidReset();
        challenge.setResetTokenHash(null);challenge.setResetExpiresAt(null);
        challenge.setUsedAt(now);codes.saveAndFlush(challenge);
        user.setPasswordHash(passwords.encode(newPassword));user.setFailedLoginAttempts(0);user.setLockedUntil(null);
        user.setStatus(UserStatus.ACTIVE);users.saveAndFlush(user);
        sessions.revokeAll(user);
        users.invalidateStaffPasskeyGrants(user.getId());
        codes.invalidateUser(user.getId(),now);
        jdbc.update("DELETE FROM identity.login_challenges WHERE user_id=?",user.getId());
        jdbc.update("DELETE FROM identity.login_questions WHERE user_id=?",user.getId());
        jdbc.update("DELETE FROM identity.passkeys WHERE user_id=?",user.getId());
        jdbc.update("INSERT INTO identity.security_events(id,user_id,event_type) VALUES (?,?,?)",UUID.randomUUID(),user.getId(),"EMAIL_PASSWORD_RESET");
        events.publishEvent(new PasswordChanged(user.getEmail(),id));
    }

    private static boolean eligible(User user) {
        return user.getStatus()==UserStatus.ACTIVE || (user.getStatus()==UserStatus.LOCKED && user.getLockedUntil()!=null);
    }
    static String fingerprint(String email) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void requireProof(String jkt) {
        if(jkt==null || jkt.isBlank()) throw new BusinessException(401,"Yêu cầu xác thực thiết bị.");
    }
    private static BusinessException invalidReset() { return new BusinessException(400,"Phiên đặt lại mật khẩu không hợp lệ, đã hết hạn hoặc đã sử dụng. Vui lòng yêu cầu mã mới."); }
    private static BusinessException invalid() { return new BusinessException(400,"Mã không hợp lệ, đã hết hạn hoặc đã sử dụng. Vui lòng kiểm tra hoặc gửi lại mã."); }
}
