package com.careflow.identity.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.identity.dto.*;
import com.careflow.identity.service.AuthService;
import com.careflow.identity.service.EkycService;
import com.careflow.identity.security.JwtService;
import com.careflow.common.exception.BusinessException;
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
import jakarta.servlet.http.HttpServletRequest;
import com.careflow.identity.security.DpopProofService;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Identity & eKYC", description = "Đăng ký, xác thực phiên và định danh điện tử")
public class AuthController {
    private final AuthService authService;
    private final EkycService ekycService;
    private final DpopProofService dpop;

    public AuthController(AuthService authService, EkycService ekycService, DpopProofService dpop) {
        this.authService = authService;
        this.ekycService = ekycService;
        this.dpop = dpop;
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
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ApiResponse.success("Đăng nhập thành công", authService.login(request, dpop.verify(http.getHeader("DPoP"), "POST", http.getRequestURI(), null, null)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Xoay refresh token và cấp cặp token mới")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        return ApiResponse.success("Làm mới token thành công", authService.refresh(request, dpop.verify(http.getHeader("DPoP"), "POST", http.getRequestURI(), null, null)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Thu hồi refresh token")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        authService.logout(request, dpop.verify(http.getHeader("DPoP"), "POST", http.getRequestURI(), null, null));
        return ApiResponse.<Void>success("Đăng xuất thành công", null);
    }

    @GetMapping("/me")
    @Operation(summary = "Xem tài khoản hiện tại")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<UserResponse> me(Authentication authentication) {
        return ApiResponse.success(authService.me(UUID.fromString(authentication.getName())));
    }

    @GetMapping("/session")
    @Operation(summary = "Xác minh phiên và trả định danh tối thiểu cho service")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<SessionStatusResponse> session(Authentication authentication) {
        if (!(authentication.getDetails() instanceof JwtService.AuthenticatedUser user)) {
            throw new BusinessException(401, "Không có phiên đã được xác minh");
        }
        return ApiResponse.success(new SessionStatusResponse(true, user.userId(), user.role(), user.sessionId(), user.expiresAt()));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Thu hồi tất cả phiên của tài khoản hiện tại")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Void> logoutAll(Authentication authentication) {
        authService.logoutAll(UUID.fromString(authentication.getName()));
        return ApiResponse.success("Đã đăng xuất tất cả phiên", null);
    }

    @PostMapping(value = "/ekyc", consumes = "multipart/form-data")
    @Operation(summary = "Endpoint eKYC một ảnh đã ngừng",
            description = "Trả 410. Dùng POST /api/auth/ekyc/verify với hai mặt căn cước, selfie và sự đồng ý của người dùng.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "Luồng một ảnh đã ngừng"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Ảnh trống hoặc không đọc được", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Ảnh vượt quá 5MB", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "Không phải JPEG/PNG hợp lệ", content = @Content)
    })
    public ApiResponse<EkycResponse> mockEkyc(
            Authentication authentication,
            @RequestPart("image") MultipartFile image) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.success(ekycService.verify(userId, image));
    }
}
