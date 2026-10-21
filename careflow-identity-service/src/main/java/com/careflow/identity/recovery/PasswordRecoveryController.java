package com.careflow.identity.recovery;

import com.careflow.common.dto.ApiResponse;
import com.careflow.identity.security.DpopProofService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/auth/recovery/email")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    private final DpopProofService proofs;
    public PasswordRecoveryController(PasswordRecoveryService recovery,DpopProofService proofs) { this.recovery=recovery;this.proofs=proofs; }
    @PostMapping("/request")
    public ResponseEntity<?> request(@Valid @RequestBody Request body,HttpServletRequest http) {
        var result=recovery.request(body.email(),proof(http));
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(ApiResponse.success(result));
    }
    @PostMapping("/verify")
    public ResponseEntity<?> verify(@Valid @RequestBody Verify body,HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(recovery.verify(body.requestId(),body.code(),proof(http))));
    }
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@Valid @RequestBody Confirm body,HttpServletRequest http) {
        recovery.confirm(body.requestId(),body.resetToken(),body.newPassword(),proof(http));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(Map.of("reset",true)));
    }
    private String proof(HttpServletRequest http) { return proofs.verify(http.getHeader("DPoP"),http.getMethod(),http.getRequestURI(),null,null); }
    public record Request(@NotBlank @Email @Size(max=255) String email) {}
    public record Verify(@NotNull UUID requestId,@NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    public record Confirm(@NotNull UUID requestId,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String resetToken,
        @NotBlank @Size(min=8,max=72) @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$") String newPassword) {}
}
