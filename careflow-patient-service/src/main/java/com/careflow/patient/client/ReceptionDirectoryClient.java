package com.careflow.patient.client;

import com.careflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;
import java.util.List;

@FeignClient(name = "hospital-directory-service", contextId = "patientReceptionDirectory", path = "/api/directory")
public interface ReceptionDirectoryClient {
    @GetMapping("/rooms")
    ApiResponse<List<ReceptionRoom>> rooms();

    @GetMapping("/staff/{userId}/room-access")
    ApiResponse<Boolean> roomAccess(@PathVariable("userId") UUID userId, @RequestParam("roomId") String roomId);

    record ReceptionRoom(String id, String roomType, Boolean isActive) {}
}
