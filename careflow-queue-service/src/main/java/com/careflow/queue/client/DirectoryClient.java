package com.careflow.queue.client;

import com.careflow.common.dto.ApiResponse;
import com.careflow.queue.client.dto.DoctorAssignmentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "hospital-directory-service", path = "/api/directory")
public interface DirectoryClient {
    @GetMapping("/doctors/{userId}")
    ApiResponse<DoctorAssignmentResponse> getDoctorByUserId(@PathVariable("userId") UUID userId);

    @GetMapping("/staff/{userId}/room-access")
    ApiResponse<Boolean> hasStaffRoomAccess(
            @PathVariable("userId") UUID userId,
            @RequestParam("roomId") String roomId);
}
