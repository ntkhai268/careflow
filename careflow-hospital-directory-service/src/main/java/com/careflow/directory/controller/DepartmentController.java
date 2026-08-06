package com.careflow.directory.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.directory.dto.DepartmentResponse;
import com.careflow.directory.dto.RoomResponse;
import com.careflow.directory.dto.request.CreateDepartmentRequest;
import com.careflow.directory.dto.request.CreateRoomRequest;
import com.careflow.directory.dto.request.UpdateDepartmentRequest;
import com.careflow.directory.dto.request.UpdateRoomRequest;
import com.careflow.directory.service.DirectoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/directory")
@RequiredArgsConstructor
public class DepartmentController {

    private final DirectoryService directoryService;

    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getAllDepartments() {
        return ResponseEntity.ok(ApiResponse.success(directoryService.getAllDepartments()));
    }

    @GetMapping("/departments/{code}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getDepartmentByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(directoryService.getDepartmentByCode(code)));
    }

    @PostMapping("/departments")
    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        checkAdminRole(userRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(directoryService.createDepartment(request)));
    }

    @PutMapping("/departments/{code}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> updateDepartment(
            @PathVariable String code,
            @Valid @RequestBody UpdateDepartmentRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        checkAdminRole(userRole);
        return ResponseEntity.ok(ApiResponse.success(directoryService.updateDepartment(code, request)));
    }

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getAllRooms(
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) String roomType) {
        return ResponseEntity.ok(ApiResponse.success(directoryService.getAllRooms(departmentCode, roomType)));
    }

    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        checkAdminRole(userRole);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(directoryService.createRoom(request)));
    }

    @PutMapping("/rooms/{id}")
    public ResponseEntity<ApiResponse<RoomResponse>> updateRoom(
            @PathVariable String id,
            @Valid @RequestBody UpdateRoomRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        checkAdminRole(userRole);
        return ResponseEntity.ok(ApiResponse.success(directoryService.updateRoom(id, request)));
    }

    private void checkAdminRole(String userRole) {
        if (userRole != null && !userRole.isBlank() && !userRole.toUpperCase().contains("ADMIN")) {
            throw new BusinessException(403, "Chỉ Quản trị viên (ADMIN) mới có quyền thực hiện thao tác này");
        }
    }
}
