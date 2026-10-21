package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.dto.*;
import com.careflow.identity.repository.*;
import com.careflow.identity.registration.EmailRegistrationService;
import com.careflow.identity.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class DeviceKeyMigrationService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final RefreshTokenService sessions;
    private final JwtService jwt;
    public DeviceKeyMigrationService(UserRepository users,JdbcTemplate jdbc,PasswordEncoder passwords,RefreshTokenService sessions,JwtService jwt) {
        this.users=users;this.jdbc=jdbc;this.passwords=passwords;this.sessions=sessions;this.jwt=jwt;
    }
    @Transactional(noRollbackFor=BusinessException.class)
    public LoginResponse rotate(JwtService.AuthenticatedUser old,String newJkt,String password,UUID credentialId,String deviceToken) {
        var user=users.findByIdForUpdate(old.userId()).orElseThrow(()->new BusinessException(401,"Phiên không hợp lệ."));
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM identity.refresh_tokens WHERE id=? AND user_id=? AND dpop_jkt=? AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP)",Boolean.class,old.sessionId(),old.userId(),old.dpopJkt())))
            throw new BusinessException(401,"Phiên đã bị thu hồi. Vui lòng đăng nhập lại.");
        if(user.getRole()!=UserRole.PATIENT || user.getStatus()!=UserStatus.ACTIVE) throw new BusinessException(403,"Tài khoản không thể chuyển khóa thiết bị.");
        boolean biometric=password==null || password.isEmpty();
        if(!biometric) {
            if(password.getBytes(StandardCharsets.UTF_8).length>72 || !passwords.matches(password,user.getPasswordHash())) {
                user.setFailedLoginAttempts(user.getFailedLoginAttempts()+1);
                if(user.getFailedLoginAttempts()>=5) { user.setStatus(UserStatus.LOCKED);user.setLockedUntil(Instant.now().plusSeconds(900)); }
                users.save(user);throw new BusinessException(401,"Không xác minh được mật khẩu để chuyển khóa.");
            }
        } else {
            var rows=jdbc.queryForList("SELECT secret_hash FROM identity.biometric_devices WHERE id=? AND user_id=? AND dpop_jkt=? AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",credentialId,old.userId(),old.dpopJkt());
            if(rows.isEmpty() || deviceToken==null || !MessageDigest.isEqual(EmailRegistrationService.hash(deviceToken).getBytes(StandardCharsets.US_ASCII),((String)rows.getFirst().get("secret_hash")).getBytes(StandardCharsets.US_ASCII)))
                throw new BusinessException(401,"Không xác minh được đăng ký sinh trắc học.");
        }
        if(old.dpopJkt().equals(newJkt)) throw new BusinessException(409,"Khóa mới phải khác khóa đang sử dụng.");
        jdbc.update("UPDATE identity.refresh_tokens SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND dpop_jkt=? AND revoked_at IS NULL",old.userId(),old.dpopJkt());
        jdbc.update("UPDATE identity.biometric_devices SET dpop_jkt=? WHERE user_id=? AND dpop_jkt=? AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP",newJkt,old.userId(),old.dpopJkt());
        var issued=biometric?sessions.replaceForDevice(user,newJkt):sessions.issue(user,newJkt);
        jdbc.update("INSERT INTO identity.security_events(id,user_id,event_type) VALUES (?,?,?)",UUID.randomUUID(),old.userId(),"DEVICE_KEY_MIGRATED");
        return new LoginResponse(jwt.issue(user,issued.sessionId()),issued.value(),"Bearer",jwt.expirationSeconds(),issued.expiresInSeconds(),UserResponse.from(user));
    }
}
