package com.careflow.directory.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.directory.dto.DoctorProfileResponse;
import com.careflow.directory.service.DirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/directory/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DirectoryService directoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DoctorProfileResponse>>> getDoctors(
            @RequestParam(required = false) String departmentCode) {
        return ResponseEntity.ok(ApiResponse.success(directoryService.getDoctors(departmentCode)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<DoctorProfileResponse>> getDoctorByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(directoryService.getDoctorByUserId(userId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<DoctorProfileResponse>> getMyDoctorProfile(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new BusinessException(401, "Thiếu header X-User-Id xác thực người dùng");
        }
        UUID userId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(ApiResponse.success(directoryService.getDoctorByUserId(userId)));
    }
}
