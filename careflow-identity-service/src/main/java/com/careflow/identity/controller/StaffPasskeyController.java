package com.careflow.identity.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.identity.security.*;
import com.careflow.identity.service.StaffPasskeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/auth/staff-passkeys")
public class StaffPasskeyController {
    private final StaffPasskeyService staff;
    private final SessionStepUp stepUp;
    private final DpopProofService dpop;
    public StaffPasskeyController(StaffPasskeyService staff,SessionStepUp stepUp,DpopProofService dpop) { this.staff=staff;this.stepUp=stepUp;this.dpop=dpop; }
    @GetMapping("/accounts")
    public ResponseEntity<?> accounts(Authentication authentication,@RequestParam(defaultValue="") String query) {
        if(authentication==null || !(authentication.getDetails() instanceof JwtService.AuthenticatedUser actor) || actor.role()!=com.careflow.identity.domain.UserRole.ADMIN)
            throw new com.careflow.common.exception.BusinessException(403,"Chỉ quản trị viên được xem danh sách nhân viên.");
        return ok(staff.accounts(query));
    }
    @PostMapping("/grants")
    public ResponseEntity<?> issue(Authentication authentication,@Valid @RequestBody Grant request) {
        var actor=stepUp.requireFreshPasskey(authentication,true);
        return ok(staff.issue(actor.userId(),request.userId(),request.recover(),request.identityCheckReference()));
    }
    @PostMapping("/enroll/options")
    public ResponseEntity<?> options(@Valid @RequestBody Redeem request,HttpServletRequest http) throws Exception {
        String jkt=dpop.verify(http.getHeader("DPoP"),"POST",http.getRequestURI(),null,null);
        return ok(staff.enrollment(request.grantId(),request.emailCode(),request.activationCode(),jkt));
    }
    @PostMapping("/enroll/finish")
    public ResponseEntity<?> finish(@Valid @RequestBody Finish request,HttpServletRequest http) {
        staff.finish(request.requestId(),request.credential(),dpop.verify(http.getHeader("DPoP"),"POST",http.getRequestURI(),null,null));return ok(Map.of("saved",true));
    }
    @PostMapping("/self/options")
    public ResponseEntity<?> self(Authentication authentication) throws Exception {
        var user=stepUp.requireFreshPasskey(authentication,false);return ok(staff.selfOptions(user.userId(),user.dpopJkt()));
    }
    @PostMapping("/self/remove")
    public ResponseEntity<?> remove(Authentication authentication,@Valid @RequestBody Remove request) {
        var user=stepUp.requireFreshPasskey(authentication,false);staff.remove(user.userId(),request.credentialId());return ok(Map.of("removed",true));
    }
    private ResponseEntity<?> ok(Object value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(value)); }
    public record Grant(@NotNull UUID userId,boolean recover,@NotBlank @Size(min=8,max=300) String identityCheckReference) { }
    public record Redeem(@NotNull UUID grantId,@NotBlank @Size(min=43,max=43) String emailCode,@NotBlank @Size(min=43,max=43) String activationCode) { }
    public record Finish(@NotNull UUID requestId,@NotBlank @Size(max=30000) String credential) { }
    public record Remove(@NotBlank @Size(max=2048) String credentialId) { }
}
