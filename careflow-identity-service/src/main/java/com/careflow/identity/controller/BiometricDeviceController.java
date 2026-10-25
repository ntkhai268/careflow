package com.careflow.identity.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.identity.service.BiometricDeviceService;
import com.careflow.identity.security.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/auth")
public class BiometricDeviceController {
    private final BiometricDeviceService devices;private final DpopProofService proofs;
    public BiometricDeviceController(BiometricDeviceService devices,DpopProofService proofs) { this.devices=devices;this.proofs=proofs; }
    @PostMapping("/biometric-devices")
    public ResponseEntity<?> enroll(Authentication auth,@Valid @RequestBody Enroll body) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiResponse.success(devices.enroll(UUID.fromString(auth.getName()),body.password(),jkt(auth))));
    }
    @PostMapping("/login/biometric")
    public ResponseEntity<?> login(@Valid @RequestBody Login body,HttpServletRequest http) {
        String jkt=proofs.verify(http.getHeader("DPoP"),http.getMethod(),http.getRequestURI(),null,null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(devices.login(body.credentialId(),body.deviceToken(),jkt)));
    }
    @DeleteMapping("/biometric-devices/{id}")
    public ResponseEntity<?> disable(Authentication auth,@PathVariable UUID id) {
        devices.disable(UUID.fromString(auth.getName()),id,jkt(auth));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(Map.of("disabled",true)));
    }
    private String jkt(Authentication auth) {
        if(auth.getDetails() instanceof JwtService.AuthenticatedUser user) return user.dpopJkt();
        throw new BusinessException(401,"Yêu cầu xác thực thiết bị.");
    }
    public record Enroll(@NotBlank @Size(max=72) String password) {}
    public record Login(@NotNull UUID credentialId,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String deviceToken) {}
}
