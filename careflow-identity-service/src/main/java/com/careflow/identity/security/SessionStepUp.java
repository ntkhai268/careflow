package com.careflow.identity.security;

import com.careflow.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SessionStepUp {
    private final JdbcTemplate jdbc;
    public SessionStepUp(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public JwtService.AuthenticatedUser requireFreshPasskey(Authentication authentication,boolean admin) {
        if(authentication==null || !(authentication.getDetails() instanceof JwtService.AuthenticatedUser user))
            throw new BusinessException(401,"Vui lòng đăng nhập lại.");
        if(admin && user.role()!=com.careflow.identity.domain.UserRole.ADMIN)
            throw new BusinessException(403,"Chỉ quản trị viên được cấp quyền passkey nhân viên.");
        if(user.role()==com.careflow.identity.domain.UserRole.PATIENT)
            throw new BusinessException(403,"Chức năng dành cho tài khoản nội bộ.");
        boolean recent=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM identity.refresh_tokens WHERE id=? AND user_id=? AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP AND authentication_method='PASSKEY' AND authenticated_at>CURRENT_TIMESTAMP-interval '5 minutes')",Boolean.class,user.sessionId(),user.userId()));
        if(!recent) throw new BusinessException(403,"Vui lòng đăng nhập lại bằng passkey trong vòng 5 phút trước thao tác này.");
        return user;
    }
}
