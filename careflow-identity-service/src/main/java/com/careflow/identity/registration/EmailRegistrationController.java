package com.careflow.identity.registration;

import com.careflow.common.dto.ApiResponse;
import com.careflow.identity.dto.RegisterRequest;
import com.careflow.identity.security.DpopProofService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/api/auth/registration/email")
public class EmailRegistrationController {
    private final EmailRegistrationService registrations;
    private final DpopProofService dpop;
    public EmailRegistrationController(EmailRegistrationService registrations,DpopProofService dpop) { this.registrations=registrations;this.dpop=dpop; }
    @PostMapping("/request")
    public ResponseEntity<?> request(@Valid @RequestBody RegisterRequest request,HttpServletRequest http) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(ApiResponse.success(registrations.request(request,proof(http))));
    }
    @PostMapping("/verify")
    public ResponseEntity<?> verify(@Valid @RequestBody Verify request,HttpServletRequest http) {
        registrations.verify(request.requestId(),request.code(),proof(http));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(Map.of("verified",true)));
    }
    private String proof(HttpServletRequest http) { return dpop.verify(http.getHeader("DPoP"),"POST",http.getRequestURI(),null,null); }
    public record Verify(@NotNull UUID requestId,@NotBlank @Pattern(regexp="[0-9]{6}") String code) { }
}
