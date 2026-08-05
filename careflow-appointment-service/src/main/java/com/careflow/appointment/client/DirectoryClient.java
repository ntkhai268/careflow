package com.careflow.appointment.client;

import com.careflow.appointment.client.dto.DepartmentResponse;
import com.careflow.appointment.client.dto.RoomResponse;
import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "hospital-directory-service", path = "/api/directory")
public interface DirectoryClient {

    @GetMapping("/departments/{code}")
    ApiResponse<DepartmentResponse> getDepartmentByCode(@PathVariable("code") String code);

    @GetMapping("/departments")
    ApiResponse<List<DepartmentResponse>> getAllDepartments();

    @GetMapping("/rooms")
    ApiResponse<List<RoomResponse>> getAllRooms();
}
