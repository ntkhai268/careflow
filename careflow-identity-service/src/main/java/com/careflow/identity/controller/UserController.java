package com.careflow.identity.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.identity.dto.UpdateUserStatusRequest;
import com.careflow.identity.dto.UserResponse;
import com.careflow.identity.service.AuthService;
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
    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Khóa, mở khóa hoặc vô hiệu hóa tài khoản")
    public ApiResponse<UserResponse> updateStatus(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(authService.updateStatus(id, request.status()));
    }
}
