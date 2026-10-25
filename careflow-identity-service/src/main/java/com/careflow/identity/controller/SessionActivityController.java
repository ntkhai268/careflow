package com.careflow.identity.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.identity.security.JwtService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SessionActivityController {
    private final JdbcTemplate jdbc;
    public SessionActivityController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @PostMapping("/api/auth/activity")
    public ResponseEntity<?> activity(Authentication authentication) {
        if(!(authentication.getDetails() instanceof JwtService.AuthenticatedUser user)) throw new BusinessException(401,"Phiên không hợp lệ.");
        // JWT validation already rejected idle sessions before this explicit interaction heartbeat.
        jdbc.update("UPDATE identity.refresh_tokens SET last_interactive_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND revoked_at IS NULL",user.sessionId(),user.userId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(null));
    }
}
