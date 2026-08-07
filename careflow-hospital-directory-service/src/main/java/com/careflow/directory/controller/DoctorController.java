package com.careflow.directory.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.directory.dto.DoctorProfileResponse;
import com.careflow.directory.dto.request.CreateDoctorProfileRequest;
import com.careflow.directory.dto.request.UpdateDoctorProfileRequest;
import com.careflow.directory.service.DirectoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
            throw new BusinessException(401, "Missing X-User-Id authentication header");
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(401, "Invalid X-User-Id authentication header");
        }
        return ResponseEntity.ok(ApiResponse.success(directoryService.getDoctorByUserId(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DoctorProfileResponse>> createDoctorProfile(
            @Valid @RequestBody CreateDoctorProfileRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        checkAdminRole(userRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(directoryService.createDoctorProfile(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorProfileResponse>> updateDoctorProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDoctorProfileRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        checkAdminRole(userRole);
        return ResponseEntity.ok(ApiResponse.success(directoryService.updateDoctorProfile(id, request)));
    }

    private void checkAdminRole(String userRole) {
        if (userRole == null || !"ADMIN".equalsIgnoreCase(userRole.trim())) {
            throw new BusinessException(403, "Only ADMIN can modify hospital directory data");
        }
    }
}
