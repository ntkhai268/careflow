package com.careflow.identity.controller;

import com.careflow.common.dto.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Compatibility response for clients with a cached TOTP screen. */
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {
    @RequestMapping({"", "/**"})
    public ResponseEntity<?> retired() {
        return ResponseEntity.status(410).cacheControl(CacheControl.noStore())
                .body(ApiResponse.error(410, "MFA đã được gỡ. Hãy tải lại và dùng Hình thức đăng nhập."));
    }
}
