package com.careflow.directory.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.directory.dto.StaffAssignmentResponse;
import com.careflow.directory.dto.request.StaffAssignmentRequest;
import com.careflow.directory.service.DirectoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/directory/staff")
@RequiredArgsConstructor
public class StaffAssignmentController {

    private final DirectoryService directoryService;

    @GetMapping("/assignments")
    public ResponseEntity<ApiResponse<List<StaffAssignmentResponse>>> getAssignments(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String roomId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        checkAdminRole(userRole);
        return ResponseEntity.ok(ApiResponse.success(directoryService.getStaffAssignments(userId, roomId)));
    }

    @PostMapping("/assignments")
    public ResponseEntity<ApiResponse<StaffAssignmentResponse>> createAssignment(
            @Valid @RequestBody StaffAssignmentRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        checkAdminRole(userRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(directoryService.createStaffAssignment(request)));
    }

    @PutMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<StaffAssignmentResponse>> updateAssignment(
            @PathVariable UUID id,
            @Valid @RequestBody StaffAssignmentRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        checkAdminRole(userRole);
        return ResponseEntity.ok(ApiResponse.success(directoryService.updateStaffAssignment(id, request)));
    }

    @GetMapping("/{userId}/room-access")
    public ResponseEntity<ApiResponse<Boolean>> hasRoomAccess(
            @PathVariable UUID userId,
            @RequestParam String roomId) {
        return ResponseEntity.ok(ApiResponse.success(
                directoryService.hasStaffRoomAccess(userId, roomId)));
    }

    private void checkAdminRole(String userRole) {
        if (userRole == null || !"ADMIN".equalsIgnoreCase(userRole.trim())) {
            throw new BusinessException(403, "Only ADMIN can modify or inspect staff assignments");
        }
    }
}
