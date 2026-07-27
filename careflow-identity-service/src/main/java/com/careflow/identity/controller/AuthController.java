package com.careflow.identity.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.identity.dto.*;
import com.careflow.identity.service.AuthService;
import com.careflow.identity.service.EkycService;
import com.careflow.identity.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Identity & eKYC", description = "Đăng ký, xác thực phiên và định danh điện tử giả lập")
public class AuthController {
    private final AuthService authService;
    private final EkycService ekycService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, EkycService ekycService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.ekycService = ekycService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản bệnh nhân")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Đăng ký thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Username hoặc email đã tồn tại", content = @Content)
    })
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<UserResponse>builder()
                .status(201).message("Đăng ký thành công").data(user).build());
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập bằng username hoặc email")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trả access token và refresh token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Sai thông tin đăng nhập", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Tài khoản bị khóa hoặc vô hiệu hóa", content = @Content)
    })
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Đăng nhập thành công", authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Xoay refresh token và cấp cặp token mới")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success("Làm mới token thành công", authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Thu hồi refresh token")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ApiResponse.<Void>success("Đăng xuất thành công", null);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Yêu cầu đặt lại mật khẩu",
            description = "Luôn trả phản hồi giống nhau để không làm lộ email có tồn tại hay không")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordResponse result = passwordResetService.request(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<ForgotPasswordResponse>builder()
                        .status(HttpStatus.ACCEPTED.value())
                        .message("Nếu email tồn tại, yêu cầu đặt lại mật khẩu đã được tạo")
                        .data(result)
                        .build());
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt mật khẩu mới bằng token dùng một lần")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
        return ApiResponse.<Void>success("Đặt lại mật khẩu thành công", null);
    }

    @GetMapping("/me")
    @Operation(summary = "Xem tài khoản hiện tại")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<UserResponse> me(Authentication authentication) {
        return ApiResponse.success(authService.me(UUID.fromString(authentication.getName())));
    }

    @PostMapping(value = "/ekyc", consumes = "multipart/form-data")
    @Operation(summary = "Xác thực eKYC giả lập",
            description = "MVP mock: kiểm tra ảnh JPEG/PNG thật, tối đa 5MB và trả kết quả định danh giả lập có cờ mock=true")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Xác thực giả lập thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Ảnh trống hoặc không đọc được", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Ảnh vượt quá 5MB", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "Không phải JPEG/PNG hợp lệ", content = @Content)
    })
    public ApiResponse<EkycResponse> mockEkyc(
            Authentication authentication,
            @RequestPart("image") MultipartFile image) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.success("eKYC giả lập thành công", ekycService.verify(userId, image));
    }
}
