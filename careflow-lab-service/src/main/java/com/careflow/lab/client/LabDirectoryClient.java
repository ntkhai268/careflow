package com.careflow.lab.client;

import com.careflow.common.dto.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "hospital-directory-service")
public interface LabDirectoryClient {
    @GetMapping("/api/directory/rooms")
    ApiResponse<List<Room>> getRooms();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Room(String id, String roomType, Boolean isActive) {}
}
