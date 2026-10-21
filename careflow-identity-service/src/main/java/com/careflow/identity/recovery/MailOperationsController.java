package com.careflow.identity.recovery;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.UserRole;
import com.careflow.identity.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class MailOperationsController {
    private final JdbcTemplate jdbc;
    private final boolean webhookConfigured;
    public MailOperationsController(JdbcTemplate jdbc,@Value("${resend.webhook-secret:}") String secret) { this.jdbc=jdbc;this.webhookConfigured=!secret.isBlank(); }
    @GetMapping("/api/auth/mail/operations")
    public ResponseEntity<?> status(Authentication authentication) {
        if(authentication==null || !(authentication.getDetails() instanceof JwtService.AuthenticatedUser actor) || actor.role()!=UserRole.ADMIN)
            throw new BusinessException(403,"Chỉ quản trị viên được xem tình trạng email.");
        var counts=jdbc.queryForList("SELECT state,delivery_status,count(*) AS count FROM identity.recovery_mail_outbox GROUP BY state,delivery_status");
        Integer pending=jdbc.queryForObject("SELECT count(*) FROM identity.recovery_mail_outbox WHERE state='PENDING' AND created_at<CURRENT_TIMESTAMP-interval '2 minutes'",Integer.class);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(Map.of("webhookConfigured",webhookConfigured,"pendingTooLong",pending,"counts",counts)));
    }
}
