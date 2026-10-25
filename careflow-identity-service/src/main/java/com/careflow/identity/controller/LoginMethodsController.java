package com.careflow.identity.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.identity.dto.LoginResponse;
import com.careflow.identity.security.DpopProofService;
import com.careflow.identity.service.LoginMethodsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class LoginMethodsController {
    private final LoginMethodsService methods;
    private final DpopProofService dpop;
    public LoginMethodsController(LoginMethodsService methods,DpopProofService dpop) { this.methods=methods;this.dpop=dpop; }
    @GetMapping("/login-methods")
    public ResponseEntity<?> status(Authentication auth) { return ok(methods.status(id(auth))); }
    @PostMapping("/login-methods/question")
    public ResponseEntity<?> question(Authentication auth,@Valid @RequestBody QuestionSetup r) {
        methods.saveQuestion(id(auth),r.password(),r.questionId(),r.answer());return ok(Map.of("saved",true));
    }
    @PostMapping("/login/question")
    public ResponseEntity<?> login(@Valid @RequestBody QuestionLogin r,HttpServletRequest http) {
        return ok(methods.questionLogin(r.usernameOrEmail(),r.questionId(),r.answer(),proof(http)));
    }
    @PostMapping("/recovery/question")
    public ResponseEntity<?> reset(@Valid @RequestBody QuestionReset r,HttpServletRequest http) {
        throw new BusinessException(410,"Quên mật khẩu chỉ hỗ trợ mã xác nhận gửi qua email.");
    }
    @PostMapping("/passkeys/register/options")
    public ResponseEntity<?> registration(Authentication auth,@Valid @RequestBody Password r,HttpServletRequest http) throws Exception {
        return ok(methods.registrationOptions(id(auth),r.password(),proof(http)));
    }
    @PostMapping("/passkeys/register/finish")
    public ResponseEntity<?> register(Authentication auth,@Valid @RequestBody Finish r,HttpServletRequest http) {
        methods.register(id(auth),r.requestId(),r.credential(),proof(http));return ok(Map.of("saved",true));
    }
    @PostMapping("/passkeys/remove")
    public ResponseEntity<?> remove(Authentication auth,@Valid @RequestBody Remove r) {
        methods.remove(id(auth),r.credentialId(),r.password());return ok(Map.of("removed",true));
    }
    @PostMapping("/passkeys/login/options")
    public ResponseEntity<?> options(@Valid @RequestBody Start r,HttpServletRequest http) throws Exception {
        return ok(methods.assertionOptions(r.usernameOrEmail(),false,proof(http)));
    }
    @PostMapping("/passkeys/login/finish")
    public ResponseEntity<?> finish(@Valid @RequestBody Finish r,HttpServletRequest http) {
        return ok(methods.assertion(r.requestId(),r.credential(),proof(http),null));
    }
    @PostMapping("/recovery/passkey/options")
    public ResponseEntity<?> recoveryOptions(@Valid @RequestBody Start r,HttpServletRequest http) throws Exception {
        throw new BusinessException(410,"Quên mật khẩu chỉ hỗ trợ mã xác nhận gửi qua email.");
    }
    @PostMapping("/recovery/passkey/finish")
    public ResponseEntity<?> recoveryFinish(@Valid @RequestBody ResetFinish r,HttpServletRequest http) {
        throw new BusinessException(410,"Quên mật khẩu chỉ hỗ trợ mã xác nhận gửi qua email.");
    }
    private UUID id(Authentication auth) { return UUID.fromString(auth.getName()); }
    private String proof(HttpServletRequest r) {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getDetails() instanceof com.careflow.identity.security.JwtService.AuthenticatedUser user) return user.dpopJkt();
        return dpop.verify(r.getHeader("DPoP"),r.getMethod(),r.getRequestURI(),null,null); }
    private ResponseEntity<?> ok(Object data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data)); }
    public record Password(@NotBlank @Size(max=72) String password) {}
    public record Start(@NotBlank @Size(max=255) String usernameOrEmail) {}
    public record QuestionSetup(@NotBlank @Size(max=72) String password,@NotBlank String questionId,@NotBlank @Size(min=8,max=72) String answer) {}
    public record QuestionLogin(@NotBlank @Size(max=255) String usernameOrEmail,@NotBlank String questionId,@NotBlank @Size(max=72) String answer) {}
    public record QuestionReset(@NotBlank @Size(max=255) String usernameOrEmail,@NotBlank String questionId,@NotBlank @Size(max=72) String answer,
            @NotBlank @Size(min=8,max=72) @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$") String newPassword) {}
    public record Finish(@NotNull UUID requestId,@NotBlank @Size(max=30000) String credential) {}
    public record ResetFinish(@NotNull UUID requestId,@NotBlank @Size(max=30000) String credential,
            @NotBlank @Size(min=8,max=72) @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$") String newPassword) {}
    public record Remove(@NotBlank @Size(max=72) String password,@NotBlank String credentialId) {}
}
