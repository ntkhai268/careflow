package com.careflow.appointment.client;

import com.careflow.appointment.client.dto.DepartmentResponse;
import com.careflow.appointment.client.dto.DoctorProfileResponse;
import com.careflow.appointment.client.dto.RoomResponse;
import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "hospital-directory-service", path = "/api/directory")
public interface DirectoryClient {

    @GetMapping("/departments/{code}")
    ApiResponse<DepartmentResponse> getDepartmentByCode(@PathVariable("code") String code);

    @GetMapping("/departments")
    ApiResponse<List<DepartmentResponse>> getAllDepartments();

    @GetMapping("/rooms")
    ApiResponse<List<RoomResponse>> getAllRooms();

    @GetMapping("/doctors/{userId}")
    ApiResponse<DoctorProfileResponse> getDoctorByUserId(@PathVariable("userId") java.util.UUID userId);

    @GetMapping("/doctors")
    ApiResponse<List<DoctorProfileResponse>> getDoctors(
            @RequestParam("departmentCode") String departmentCode);

    @GetMapping("/staff/{userId}/room-access")
    ApiResponse<Boolean> hasStaffRoomAccess(
            @PathVariable("userId") UUID userId,
            @RequestParam("roomId") String roomId);
}
