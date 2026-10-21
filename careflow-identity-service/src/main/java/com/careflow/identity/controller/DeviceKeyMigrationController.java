package com.careflow.identity.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.identity.security.*;
import com.careflow.identity.service.DeviceKeyMigrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class DeviceKeyMigrationController {
    private final DeviceKeyMigrationService devices;
    private final DpopProofService dpop;
    public DeviceKeyMigrationController(DeviceKeyMigrationService devices,DpopProofService dpop) { this.devices=devices;this.dpop=dpop; }
    @PostMapping("/api/auth/device-key/rotate")
    public ResponseEntity<?> rotate(Authentication authentication,@Valid @RequestBody Rotation request,HttpServletRequest http) {
        if(!(authentication.getDetails() instanceof JwtService.AuthenticatedUser user)) throw new BusinessException(401,"Phiên không hợp lệ.");
        String token=http.getHeader("Authorization").substring(7);
        String next=dpop.verify(request.newKeyProof(),"POST",http.getRequestURI(),token,null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(devices.rotate(user,next,request.password(),request.credentialId(),request.deviceToken())));
    }
    public record Rotation(@NotBlank @Size(max=8192) String newKeyProof,@Size(max=72) String password,UUID credentialId,@Size(max=100) String deviceToken) { }
}
