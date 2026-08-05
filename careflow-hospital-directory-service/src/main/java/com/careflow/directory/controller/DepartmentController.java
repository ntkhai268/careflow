package com.careflow.directory.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.directory.dto.DepartmentResponse;
import com.careflow.directory.dto.RoomResponse;
import com.careflow.directory.service.DirectoryService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getAllRooms(
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) String roomType) {
        return ResponseEntity.ok(ApiResponse.success(directoryService.getAllRooms(departmentCode, roomType)));
    }
}
