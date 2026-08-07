package com.careflow.consultation.client;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.consultation.client.dto.AppointmentResponse;
import com.careflow.consultation.client.dto.UpdateAppointmentStatusRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "appointment-service", path = "/api/appointments")
public interface AppointmentClient {

    @GetMapping("/{id}")
    ApiResponse<AppointmentResponse> getAppointmentById(
            @PathVariable("id") UUID id,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role);

    @PutMapping("/{id}/status")
    ApiResponse<AppointmentResponse> updateAppointmentStatus(
            @PathVariable("id") UUID id,
            @RequestBody UpdateAppointmentStatusRequest request,
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestHeader(AppConstants.HEADER_USER_ROLE) String role);
}
