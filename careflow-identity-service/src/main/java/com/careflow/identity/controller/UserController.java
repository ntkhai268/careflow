package com.careflow.identity.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.identity.dto.UpdateUserStatusRequest;
import com.careflow.identity.dto.UpdateUserRoleRequest;
import com.careflow.identity.dto.UserResponse;
import com.careflow.identity.administration.StaffAccountService;
import com.careflow.identity.domain.UserRole;
import com.careflow.identity.domain.UserStatus;
import com.careflow.identity.security.SessionStepUp;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Administration", description = "Quản trị trạng thái tài khoản")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final StaffAccountService staff;
    private final SessionStepUp stepUp;

    public UserController(StaffAccountService staff, SessionStepUp stepUp) {
        this.staff = staff;
        this.stepUp = stepUp;
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StaffAccountService.Page>> staff(
            @RequestParam(defaultValue="") String query, @RequestParam(required=false) UserRole role,
            @RequestParam(required=false) UserStatus status, @RequestParam(defaultValue="0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(staff.list(query,role,status,page)));
    }

    @GetMapping("/staff/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> staffById(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(staff.find(id)));
    }

    @GetMapping("/staff/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<java.util.List<UserRole>> staffRoles() {
        return ApiResponse.success(java.util.Arrays.stream(UserRole.values()).filter(role->role!=UserRole.PATIENT).toList());
    }

    @PostMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> createStaff(Authentication authentication,
            @RequestHeader("Idempotency-Key") UUID requestId, @Valid @RequestBody StaffAccountService.Create request) {
        var actor=stepUp.requireFreshPasskey(authentication,true);
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(ApiResponse.success(staff.create(actor,requestId,request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Khóa, mở khóa hoặc vô hiệu hóa tài khoản")
    public ApiResponse<UserResponse> updateStatus(Authentication authentication, @PathVariable UUID id,
                                                   @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(staff.status(stepUp.requireFreshPasskey(authentication,true),id,request.status()));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Gán vai trò cho tài khoản")
    public ApiResponse<UserResponse> updateRole(Authentication authentication, @PathVariable UUID id,
                                                @Valid @RequestBody UpdateUserRoleRequest request) {
        return ApiResponse.success(staff.role(stepUp.requireFreshPasskey(authentication,true),id,request.role()));
    }
}
