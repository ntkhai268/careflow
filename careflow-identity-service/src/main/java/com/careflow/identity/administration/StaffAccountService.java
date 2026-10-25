package com.careflow.identity.administration;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.identity.domain.*;
import com.careflow.identity.dto.UserResponse;
import com.careflow.identity.repository.UserRepository;
import com.careflow.identity.security.JwtService;
import com.careflow.identity.service.AuthService;
import jakarta.validation.constraints.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.*;

@Service
public class StaffAccountService {
    private static final long ADMINISTRATION_LOCK = 0x43464c4f5741444dL;
    private static final String COLUMNS = "u.id,u.username,u.email,u.role,u.status,p.display_name AS \"displayName\","
            + "u.last_login_at AS \"lastLoginAt\",(SELECT count(*) FROM identity.passkeys k WHERE k.user_id=u.id) AS \"passkeyCount\"";
    private static final String FROM = " FROM identity.users u LEFT JOIN identity.staff_account_provisioning p ON p.user_id=u.id ";
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuthService auth;

    public StaffAccountService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder passwords, AuthService auth) {
        this.jdbc = jdbc; this.users = users; this.passwords = passwords; this.auth = auth;
    }

    public record Create(@NotBlank @Pattern(regexp="[A-Za-z0-9._-]{3,50}") String username,
                         @NotBlank @Email @Size(max=255) String email,
                         @NotBlank @Size(min=2,max=100) String displayName, @NotNull UserRole role) { }
    public record Page(List<Map<String,Object>> items, long total, int page, int size) { }

    @Transactional(readOnly=true)
    public Page list(String query, UserRole role, UserStatus status, int page) {
        if (query.length()>100 || page<0 || page>10000 || role==UserRole.PATIENT)
            throw new BusinessException(400,"Bộ lọc tài khoản không hợp lệ.");
        String search="%"+query.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String where=" WHERE u.role<>'PATIENT' AND (lower(u.username) LIKE ? ESCAPE '!' OR lower(u.email) LIKE ? ESCAPE '!' OR lower(coalesce(p.display_name,'')) LIKE ? ESCAPE '!')";
        var args=new ArrayList<Object>(List.of(search,search,search));
        if(role!=null){where+=" AND u.role=?";args.add(role.name());}
        if(status!=null){where+=" AND u.status=?";args.add(status.name());}
        Long total=jdbc.queryForObject("SELECT count(*)"+FROM+where,Long.class,args.toArray());
        args.add(25);args.add(page*25);
        return new Page(jdbc.queryForList("SELECT "+COLUMNS+FROM+where+" ORDER BY u.username,u.id LIMIT ? OFFSET ?",args.toArray()),total==null?0:total,page,25);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> find(UUID id) {
        var rows=jdbc.queryForList("SELECT "+COLUMNS+FROM+" WHERE u.id=? AND u.role<>'PATIENT'",id);
        if(rows.isEmpty())throw new ResourceNotFoundException("StaffAccount","id",id);
        return rows.getFirst();
    }

    @Transactional
    public Map<String,Object> create(JwtService.AuthenticatedUser actor, UUID requestId, Create request) {
        lockAndRequireAdministrator(actor);
        String username=request.username().strip().toLowerCase(Locale.ROOT),email=request.email().strip().toLowerCase(Locale.ROOT);
        String name=Normalizer.normalize(request.displayName().strip(),Normalizer.Form.NFC);
        if(request.role()==UserRole.PATIENT || name.length()<2 || name.codePoints().anyMatch(Character::isISOControl))
            throw new BusinessException(400,"Thông tin nhân viên không hợp lệ.");
        String fingerprint=fingerprint(username,email,name,request.role().name());
        var replay=jdbc.queryForList("SELECT user_id,request_fingerprint FROM identity.staff_account_provisioning WHERE created_by=? AND request_id=?",actor.userId(),requestId);
        if(!replay.isEmpty()){
            if(!fingerprint.equals(replay.getFirst().get("request_fingerprint")))throw new BusinessException(409,"Yêu cầu tạo tài khoản đã được dùng với thông tin khác.");
            return find((UUID)replay.getFirst().get("user_id"));
        }
        if(users.existsByUsernameIgnoreCase(username) || users.existsByEmailIgnoreCase(email))
            throw new BusinessException(409,"Tên tài khoản hoặc email đã tồn tại. Hãy tìm và tiếp tục thiết lập tài khoản đó.");
        User user=new User();user.setUsername(username);user.setEmail(email);user.setRole(request.role());
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);
        // No temporary password is exposed; this account must complete trusted passkey enrollment.
        user.setPasswordHash(passwords.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(random)));
        try{users.saveAndFlush(user);}catch(DataIntegrityViolationException conflict){throw new BusinessException(409,"Tên tài khoản hoặc email đã tồn tại.");}
        jdbc.update("INSERT INTO identity.staff_account_provisioning(user_id,created_by,request_id,request_fingerprint,display_name) VALUES (?,?,?,?,?)",user.getId(),actor.userId(),requestId,fingerprint,name);
        audit(actor.userId(),user.getId(),"CREATED",null,user.getRole().name());
        return find(user.getId());
    }

    @Transactional
    public UserResponse status(JwtService.AuthenticatedUser actor, UUID id, UserStatus status) {
        lockAndRequireAdministrator(actor);
        User target=users.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("User","id",id));
        UserStatus previous=target.getStatus();
        if(previous!=status){guardAdministrator(actor.userId(),target,status==UserStatus.ACTIVE && target.getRole()==UserRole.ADMIN);}
        UserResponse result=auth.updateStatus(id,status);
        if(previous!=status)audit(actor.userId(),id,"STATUS_CHANGED",previous.name(),status.name());
        return result;
    }

    @Transactional
    public UserResponse role(JwtService.AuthenticatedUser actor, UUID id, UserRole role) {
        lockAndRequireAdministrator(actor);
        User target=users.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("User","id",id));
        UserRole previous=target.getRole();
        if(previous!=role)guardAdministrator(actor.userId(),target,role==UserRole.ADMIN && target.getStatus()==UserStatus.ACTIVE);
        UserResponse result=auth.updateRole(id,role);
        if(previous!=role)audit(actor.userId(),id,"ROLE_CHANGED",previous.name(),role.name());
        return result;
    }

    private void lockAndRequireAdministrator(JwtService.AuthenticatedUser actor) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)",ADMINISTRATION_LOCK);
        // Recheck after waiting for the lock: another administrator may have just revoked this actor.
        Boolean valid=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM identity.users u JOIN identity.refresh_tokens s ON s.user_id=u.id WHERE u.id=? AND u.role='ADMIN' AND u.status='ACTIVE' AND s.id=? AND s.revoked_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP AND s.authentication_method='PASSKEY' AND s.authenticated_at>CURRENT_TIMESTAMP-interval '5 minutes')",Boolean.class,actor.userId(),actor.sessionId());
        if(!Boolean.TRUE.equals(valid))throw new BusinessException(403,"Vui lòng đăng nhập lại bằng passkey quản trị trước thao tác này.");
    }

    private void guardAdministrator(UUID actor, User target, boolean remainsActiveAdministrator) {
        if(actor.equals(target.getId()))throw new BusinessException(409,"Không thể tự khóa hoặc đổi quyền tài khoản đang quản trị.");
        if(target.getRole()==UserRole.ADMIN && target.getStatus()==UserStatus.ACTIVE && !remainsActiveAdministrator){
            Long others=jdbc.queryForObject("SELECT count(*) FROM identity.users u WHERE u.role='ADMIN' AND u.status='ACTIVE' AND u.id<>? AND EXISTS(SELECT 1 FROM identity.passkeys p WHERE p.user_id=u.id)",Long.class,target.getId());
            if(others==null || others==0)throw new BusinessException(409,"Phải giữ ít nhất một quản trị viên hoạt động có passkey.");
        }
    }

    private void audit(UUID actor, UUID user, String action, String previous, String value) {
        jdbc.update("INSERT INTO identity.staff_account_audit(id,actor_id,user_id,action,previous_value,new_value) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),actor,user,action,previous,value);
    }

    private static String fingerprint(String... fields) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\0",fields).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
