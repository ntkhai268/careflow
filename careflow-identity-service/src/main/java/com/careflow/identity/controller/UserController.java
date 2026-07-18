package com.careflow.identity.controller;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.identity.dto.UpdateUserStatusRequest;
import com.careflow.identity.dto.UserResponse;
import com.careflow.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<UserResponse> updateStatus(@PathVariable UUID id,
                                                   @RequestHeader(AppConstants.HEADER_USER_ROLE) String role,
                                                   @Valid @RequestBody UpdateUserStatusRequest request) {
        if (!AppConstants.ROLE_ADMIN.equals(role)) throw new BusinessException(403, "Chỉ ADMIN được quản lý tài khoản");
        return ApiResponse.success(authService.updateStatus(id, request.status()));
    }
}
