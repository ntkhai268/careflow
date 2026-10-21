package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.dto.LoginResponse;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.security.JwtService;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class LoginMethodsService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final RefreshTokenService sessions;
    private final JwtService jwt;
    private final RelyingParty rp;

    public LoginMethodsService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder passwords,
                               RefreshTokenService sessions, JwtService jwt,
                               @Value("${security.passkey.rp-id:localhost}") String rpId,
                               @Value("${security.passkey.origins:http://localhost:3000,http://localhost:5173}") String origins) {
        this.jdbc=jdbc; this.users=users; this.passwords=passwords; this.sessions=sessions; this.jwt=jwt;
        this.rp=RelyingParty.builder().identity(RelyingPartyIdentity.builder().id(rpId).name("CareFlow").build())
                .credentialRepository(new StoredCredentials()).origins(Set.of(origins.split(","))).build();
    }

    public Map<String,Object> status(UUID userId) {
        return Map.of("questionId","", "questions",Map.of(), "questionEnabled",false,
                "passkeys",jdbc.queryForList("SELECT credential_id AS id,created_at FROM identity.passkeys WHERE user_id=? ORDER BY created_at",userId));
    }

    public void saveQuestion(UUID userId, String password, String questionId, String answer) {
        throw retiredQuestion();
    }

    public LoginResponse questionLogin(String identifier,String questionId,String answer,String jkt) {
        throw retiredQuestion();
    }

    public void questionReset(String identifier,String questionId,String answer,String newPassword) {
        throw retiredQuestion();
    }

    private static BusinessException retiredQuestion() {
        return new BusinessException(410,"Câu hỏi bí mật đã ngừng hỗ trợ. Hãy dùng mật khẩu, passkey hoặc sinh trắc học; quên mật khẩu chỉ qua email.");
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public Map<String,Object> registrationOptions(UUID userId,String password,String jkt) throws Exception {
        var user=available(userId); verifyPassword(user,password);
        if(user.getRole()!=UserRole.PATIENT) throw new BusinessException(403,"Nhân viên cần xác nhận passkey gần đây hoặc mã cấp quyền từ quản trị viên.");
        return startRegistration(user,jkt,null);
    }

    @Transactional
    public Map<String,Object> trustedStaffRegistration(UUID userId,String jkt,UUID grantId) throws Exception {
        var user=available(userId);
        if(user.getRole()==UserRole.PATIENT) throw new BusinessException(403,"Chỉ dành cho nhân viên.");
        return startRegistration(user,jkt,grantId);
    }

    private Map<String,Object> startRegistration(User user,String jkt,UUID grantId) throws Exception {
        UUID userId=user.getId();
        if(jdbc.queryForObject("SELECT count(*) FROM identity.passkeys WHERE user_id=?",Integer.class,userId)>=10)
            throw new BusinessException(409,"Tối đa 10 passkey; hãy gỡ khóa cũ trước");
        var request=rp.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder().name(user.getUsername()).displayName(user.getUsername()).id(handle(userId)).build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder().residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED).build()).timeout(120000).build());
        UUID id=store(userId,"register",request.toJson(),jkt);
        if(grantId!=null) jdbc.update("UPDATE identity.login_challenges SET staff_grant_id=? WHERE id=?",grantId,id);
        return Map.of("requestId",id,"options",request.toCredentialsCreateJson());
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public void register(UUID userId,UUID requestId,String response,String jkt) {
        available(userId);
        var pending=consume(requestId,"register",jkt);
        if(!userId.equals(pending.userId())) throw invalid();
        if(pending.grantId()!=null && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM identity.staff_passkey_grants WHERE id=? AND user_id=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP AND failed_attempts<5)",Boolean.class,pending.grantId(),userId))) throw invalid();
        if(pending.grantId()==null && available(userId).getRole()!=UserRole.PATIENT) throw invalid();
        RegistrationResult result;
        try {
            result=rp.finishRegistration(FinishRegistrationOptions.builder()
                    .request(PublicKeyCredentialCreationOptions.fromJson(pending.json()))
                    .response(PublicKeyCredential.parseRegistrationResponseJson(response)).build());
        } catch(Exception exception) { throw new BusinessException(400,"Không xác minh được passkey. Hãy bắt đầu lại."); }
        jdbc.update("INSERT INTO identity.passkeys(credential_id,user_id,public_key_cose,signature_count) VALUES (?,?,?,?)",
                result.getKeyId().getId().getBase64Url(),userId,result.getPublicKeyCose().getBase64Url(),result.getSignatureCount());
        if(pending.grantId()!=null) {
            jdbc.update("UPDATE identity.staff_passkey_grants SET used_at=CURRENT_TIMESTAMP WHERE id=?",pending.grantId());
            jdbc.update("UPDATE identity.staff_passkey_grants SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL",userId);
        }
        event(userId,"PASSKEY_REGISTERED");
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public void remove(UUID userId,String credentialId,String password) {
        var user=available(userId); verifyPassword(user,password);
        if(user.getRole()!=UserRole.PATIENT) throw new BusinessException(403,"Nhân viên cần xác nhận passkey gần đây để gỡ khóa.");
        jdbc.update("DELETE FROM identity.passkeys WHERE user_id=? AND credential_id=?",userId,credentialId);
        sessions.revokeAll(user); event(userId,"PASSKEY_REMOVED");
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public Map<String,Object> assertionOptions(String identifier,boolean reset,String jkt) throws Exception {
        var user=available(findUser(identifier).orElseThrow(LoginMethodsService::invalid).getId());
        if(jkt==null || jkt.isBlank()) throw invalid();
        var request=rp.startAssertion(StartAssertionOptions.builder().username(user.getUsername())
                .userVerification(UserVerificationRequirement.REQUIRED).timeout(120000).build());
        return Map.of("requestId",store(user.getId(),reset?"reset":"login",request.toJson(),jkt),"options",request.toCredentialsGetJson());
    }

    @Transactional(noRollbackFor=BusinessException.class)
    public LoginResponse assertion(UUID requestId,String response,String jkt,String newPassword) {
        if(newPassword!=null) throw new BusinessException(410,"Quên mật khẩu chỉ hỗ trợ mã xác nhận gửi qua email.");
        var pending=consume(requestId,newPassword==null?"login":"reset",jkt);
        var user=available(pending.userId());
        try {
            var result=rp.finishAssertion(FinishAssertionOptions.builder().request(AssertionRequest.fromJson(pending.json()))
                    .response(PublicKeyCredential.parseAssertionResponseJson(response)).build());
            if(!result.isSuccess() || !user.getUsername().equals(result.getUsername()) || !handle(user.getId()).equals(result.getUserHandle())) throw invalid();
            jdbc.update("UPDATE identity.passkeys SET signature_count=? WHERE credential_id=? AND user_id=?",
                    result.getSignatureCount(),result.getCredentialId().getBase64Url(),user.getId());
        } catch(Exception exception) { failed(user); throw invalid(); }
        return issue(user,jkt);
    }

    private UUID store(UUID userId,String kind,String request,String jkt) {
        if(jkt==null || jkt.isBlank()) throw invalid();
        jdbc.update("DELETE FROM identity.login_challenges WHERE expires_at<CURRENT_TIMESTAMP");
        if(jdbc.queryForObject("SELECT count(*) FROM identity.login_challenges WHERE user_id=?",Integer.class,userId)>=5)
            throw new BusinessException(429,"Có nhiều yêu cầu đang chờ. Hãy thử lại sau 2 phút.");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO identity.login_challenges(id,user_id,kind,request_json,dpop_jkt,expires_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP+interval '2 minutes')",id,userId,kind,request,jkt);
        return id;
    }

    private record Pending(UUID userId,String json,UUID grantId) {}
    private Pending consume(UUID id,String kind,String jkt) {
        var rows=jdbc.query("DELETE FROM identity.login_challenges WHERE id=? AND kind=? AND dpop_jkt=? AND expires_at>CURRENT_TIMESTAMP RETURNING user_id,request_json,staff_grant_id",
                (rs,n)->new Pending(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class)),id,kind,jkt);
        if(rows.isEmpty()) throw new BusinessException(401,"Yêu cầu hết hạn, đã sử dụng hoặc không thuộc phiên này");
        return rows.getFirst();
    }

    private Optional<User> findUser(String identifier) {
        String login=identifier.strip().toLowerCase(Locale.ROOT);
        return users.findByUsernameIgnoreCaseOrEmailIgnoreCase(login,login);
    }
    private User available(UUID id) {
        var user=users.findByIdForUpdate(id).orElseThrow(LoginMethodsService::invalid);
        if(user.getStatus()==UserStatus.LOCKED && user.getLockedUntil()!=null && !user.getLockedUntil().isAfter(Instant.now())) {
            user.setStatus(UserStatus.ACTIVE);user.setLockedUntil(null);user.setFailedLoginAttempts(0);
        }
        if(user.getStatus()!=UserStatus.ACTIVE) throw new BusinessException(403,"Tài khoản đang bị khóa hoặc vô hiệu hóa");
        return user;
    }
    private void verifyPassword(User user,String password) {
        if(!passwords.matches(password,user.getPasswordHash())) { failed(user);throw invalid(); }
        user.setFailedLoginAttempts(0);users.save(user);
    }
    private void failed(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts()+1);
        if(user.getFailedLoginAttempts()>=5) { user.setStatus(UserStatus.LOCKED);user.setLockedUntil(Instant.now().plusSeconds(900)); }
        users.save(user);
    }
    private LoginResponse issue(User user,String jkt) {
        user.setFailedLoginAttempts(0);user.setLastLoginAt(Instant.now());user.setLockedUntil(null);users.save(user);
        var refresh=sessions.issuePasskey(user,jkt);event(user.getId(),"PASSKEY_LOGIN");
        return new LoginResponse(jwt.issue(user,refresh.sessionId()),refresh.value(),"Bearer",jwt.expirationSeconds(),refresh.expiresInSeconds(),com.careflow.identity.dto.UserResponse.from(user));
    }
    private void event(UUID user,String type) { jdbc.update("INSERT INTO identity.security_events(id,user_id,event_type) VALUES (?,?,?)",UUID.randomUUID(),user,type); }
    private static BusinessException invalid() { return new BusinessException(401,"Thông tin xác thực không hợp lệ"); }
    private static ByteArray handle(UUID id) { return new ByteArray(id.toString().getBytes(StandardCharsets.UTF_8)); }
    private static ByteArray bytes(String value) { try { return ByteArray.fromBase64Url(value); } catch(Exception e) { throw new IllegalStateException("Invalid stored credential",e); } }

    private class StoredCredentials implements CredentialRepository {
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            return findUser(username).map(u->new HashSet<>(jdbc.query("SELECT credential_id FROM identity.passkeys WHERE user_id=?",(rs,n)->PublicKeyCredentialDescriptor.builder().id(bytes(rs.getString(1))).build(),u.getId()))).orElseGet(HashSet::new);
        }
        public Optional<ByteArray> getUserHandleForUsername(String username) { return findUser(username).map(u->handle(u.getId())); }
        public Optional<String> getUsernameForUserHandle(ByteArray handle) {
            try { return users.findById(UUID.fromString(new String(handle.getBytes(),StandardCharsets.UTF_8))).map(User::getUsername); } catch(Exception e) { return Optional.empty(); }
        }
        public Optional<RegisteredCredential> lookup(ByteArray id,ByteArray userHandle) { return lookupAll(id).stream().filter(c->c.getUserHandle().equals(userHandle)).findFirst(); }
        public Set<RegisteredCredential> lookupAll(ByteArray id) {
            return new HashSet<>(jdbc.query("SELECT user_id,public_key_cose,signature_count FROM identity.passkeys WHERE credential_id=?",
                    (rs,n)->RegisteredCredential.builder().credentialId(id).userHandle(handle(rs.getObject(1,UUID.class))).publicKeyCose(bytes(rs.getString(2))).signatureCount(rs.getLong(3)).build(),id.getBase64Url()));
        }
    }
}
