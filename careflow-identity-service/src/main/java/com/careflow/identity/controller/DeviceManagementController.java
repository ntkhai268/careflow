package com.careflow.identity.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.identity.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Account-scoped inventory; secrets and DPoP thumbprints never appear in responses. */
@RestController
@RequestMapping("/api/auth/devices")
public class DeviceManagementController {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    public DeviceManagementController(JdbcTemplate jdbc,UserRepository users) { this.jdbc=jdbc;this.users=users; }
    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        UUID owner=UUID.fromString(authentication.getName());
        var devices=jdbc.queryForList("SELECT id,created_at,expires_at,last_used_at FROM identity.biometric_devices WHERE user_id=? AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP ORDER BY created_at DESC",owner);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(devices));
    }
    @PostMapping("/{id}/revoke")
    @Transactional
    public ResponseEntity<?> revoke(@PathVariable UUID id,Authentication authentication) {
        UUID owner=UUID.fromString(authentication.getName());users.findByIdForUpdate(owner).orElseThrow(()->new BusinessException(401,"Phiên không hợp lệ."));
        var rows=jdbc.queryForList("SELECT dpop_jkt FROM identity.biometric_devices WHERE id=? AND user_id=? FOR UPDATE",id,owner);
        if(rows.isEmpty()) throw new BusinessException(404,"Không tìm thấy thiết bị.");
        jdbc.update("UPDATE identity.biometric_devices SET revoked_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",id,owner);
        jdbc.update("UPDATE identity.refresh_tokens SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND dpop_jkt=? AND revoked_at IS NULL",owner,rows.getFirst().get("dpop_jkt"));
        jdbc.update("INSERT INTO identity.security_events(id,user_id,event_type) VALUES (?,?,?)",UUID.randomUUID(),owner,"DEVICE_REMOTELY_REVOKED");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(Map.of("revoked",true)));
    }
}
