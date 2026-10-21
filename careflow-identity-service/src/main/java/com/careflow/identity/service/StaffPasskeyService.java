package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.UserRole;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.recovery.RecoveryMailOutbox;
import com.careflow.identity.registration.EmailRegistrationService;
import com.careflow.identity.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
public class StaffPasskeyService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final RefreshTokenService sessions;
    private final LoginMethodsService methods;
    private final RecoveryMailOutbox outbox;
    private final SecureRandom random=new SecureRandom();
    public StaffPasskeyService(JdbcTemplate jdbc,UserRepository users,RefreshTokenService sessions,LoginMethodsService methods,RecoveryMailOutbox outbox) {
        this.jdbc=jdbc;this.users=users;this.sessions=sessions;this.methods=methods;this.outbox=outbox;
    }
    public record Issued(UUID grantId,String activationCode,int expiresInSeconds) { }
    public List<Map<String,Object>> accounts(String query) {
        if(query.length()>100) throw new BusinessException(400,"Từ khóa quá dài.");
        String filter="%"+query.strip().toLowerCase(Locale.ROOT)+"%";
        return jdbc.queryForList("SELECT u.id,u.username,u.email,u.role,u.status,(SELECT count(*) FROM identity.passkeys p WHERE p.user_id=u.id) AS passkey_count FROM identity.users u WHERE u.role<>'PATIENT' AND (lower(u.username) LIKE ? OR lower(u.email) LIKE ?) ORDER BY u.username LIMIT 50",filter,filter);
    }

    @Transactional
    public Issued issue(UUID administrator,UUID target,boolean recover,String reference) {
        var user=users.findByIdForUpdate(target).orElseThrow(StaffPasskeyService::invalid);
        if(user.getRole()==UserRole.PATIENT || user.getStatus()!=UserStatus.ACTIVE) throw new BusinessException(403,"Chỉ cấp cho tài khoản nhân viên đang hoạt động.");
        int count=jdbc.queryForObject("SELECT count(*) FROM identity.passkeys WHERE user_id=?",Integer.class,target);
        if(count>0 && !recover) throw new BusinessException(409,"Nhân viên đã có passkey. Chỉ dùng khôi phục khi đã xác minh việc mất khóa.");
        if(administrator.equals(target)) throw new BusinessException(403,"Dùng trang Bảo mật để thêm khóa của chính bạn; không tự cấp khôi phục qua email.");
        jdbc.update("UPDATE identity.staff_passkey_grants SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL",target);
        if(recover) {
            sessions.revokeAll(user);
            jdbc.update("DELETE FROM identity.passkeys WHERE user_id=?",target);
            jdbc.update("DELETE FROM identity.login_challenges WHERE user_id=?",target);
        }
        UUID id=UUID.randomUUID();String emailCode=secret(),activation=secret();
        jdbc.update("INSERT INTO identity.staff_passkey_grants(id,user_id,token_hash,activation_hash,kind,issued_by,identity_check_reference,expires_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP+interval '15 minutes')",
                id,target,hash(emailCode),hash(activation),recover?"RECOVER":"ENROLL",administrator,reference.strip());
        outbox.enqueue(id,"STAFF_INVITE",user.getEmail(),id+"."+emailCode);
        jdbc.update("INSERT INTO identity.security_events(id,user_id,event_type) VALUES (?,?,?)",UUID.randomUUID(),target,recover?"STAFF_PASSKEY_RECOVERY_STARTED":"STAFF_PASSKEY_INVITED");
        return new Issued(id,activation,900); // Administrator delivers this second factor after identity checking.
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public Map<String,Object> enrollment(UUID id,String emailCode,String activation,String jkt) throws Exception {
        var owner=jdbc.queryForList("SELECT user_id FROM identity.staff_passkey_grants WHERE id=?",id);
        if(owner.isEmpty()) throw invalid();
        UUID userId=(UUID)owner.getFirst().get("user_id");
        users.findByIdForUpdate(userId).orElseThrow(StaffPasskeyService::invalid);
        var rows=jdbc.queryForList("SELECT * FROM identity.staff_passkey_grants WHERE id=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP AND failed_attempts<5 FOR UPDATE",id);
        if(rows.isEmpty()) throw invalid();
        var row=rows.getFirst();
        if(!sameHash(emailCode,(String)row.get("token_hash")) || !sameHash(activation,(String)row.get("activation_hash"))) {
            jdbc.update("UPDATE identity.staff_passkey_grants SET failed_attempts=failed_attempts+1 WHERE id=?",id);throw invalid();
        }
        return methods.trustedStaffRegistration(userId,jkt,id);
    }

    @Transactional
    public void finish(UUID challenge,String credential,String jkt) {
        var rows=jdbc.queryForList("SELECT user_id FROM identity.login_challenges WHERE id=? AND dpop_jkt=? AND staff_grant_id IS NOT NULL AND expires_at>CURRENT_TIMESTAMP",challenge,jkt);
        if(rows.isEmpty()) throw invalid();
        methods.register((UUID)rows.getFirst().get("user_id"),challenge,credential,jkt);
    }

    @Transactional
    public Map<String,Object> selfOptions(UUID user,String jkt) throws Exception {
        users.findByIdForUpdate(user).orElseThrow(StaffPasskeyService::invalid);
        UUID grant=UUID.randomUUID();
        jdbc.update("INSERT INTO identity.staff_passkey_grants(id,user_id,token_hash,activation_hash,kind,issued_by,identity_check_reference,expires_at) VALUES (?,?,?,?,'ENROLL',?,'SELF_PASSKEY_STEP_UP',CURRENT_TIMESTAMP+interval '2 minutes')",grant,user,hash(secret()),hash(secret()),user);
        return methods.trustedStaffRegistration(user,jkt,grant);
    }

    @Transactional
    public void remove(UUID userId,String credentialId) {
        var user=users.findByIdForUpdate(userId).orElseThrow(StaffPasskeyService::invalid);
        if(jdbc.queryForObject("SELECT count(*) FROM identity.passkeys WHERE user_id=?",Integer.class,userId)<=1)
            throw new BusinessException(409,"Không thể gỡ passkey cuối cùng. Thêm khóa dự phòng trước.");
        int removed=jdbc.update("DELETE FROM identity.passkeys WHERE user_id=? AND credential_id=?",userId,credentialId);
        if(removed==0) throw new BusinessException(404,"Không tìm thấy passkey.");
        sessions.revokeAll(user);
        jdbc.update("INSERT INTO identity.security_events(id,user_id,event_type) VALUES (?,?,?)",UUID.randomUUID(),userId,"PASSKEY_REMOVED");
    }
    private String secret() { byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(String value) { return EmailRegistrationService.hash(value); }
    private static boolean sameHash(String raw,String expected) { return raw!=null && MessageDigest.isEqual(hash(raw).getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII)); }
    private static BusinessException invalid() { return new BusinessException(401,"Quyền thiết lập passkey không hợp lệ, hết hạn hoặc đã dùng."); }
}
