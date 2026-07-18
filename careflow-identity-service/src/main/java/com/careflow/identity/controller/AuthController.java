package com.careflow.identity.controller;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.identity.dto.*;
import com.careflow.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<UserResponse>builder()
                .status(201).message("Đăng ký thành công").data(user).build());
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Đăng nhập thành công", authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@RequestHeader(AppConstants.HEADER_USER_ID) UUID userId) {
        return ApiResponse.success(authService.me(userId));
    }

    @PostMapping(value = "/ekyc", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> mockEkyc(
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestPart("image") MultipartFile image) {
        if (image.isEmpty() || image.getContentType() == null || !image.getContentType().startsWith("image/")) {
            throw new BusinessException(400, "Cần tải lên một tệp ảnh CCCD hợp lệ");
        }
        return ApiResponse.success("eKYC giả lập thành công", Map.of(
                "userId", userId,
                "documentNumber", "001099000001",
                "fullName", "NGUYEN VAN A",
                "dateOfBirth", "1999-01-01",
                "confidence", 0.98,
                "verified", true));
    }
}
