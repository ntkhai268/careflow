package com.careflow.appointment.controller;

import com.careflow.appointment.service.RoomScheduleService;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments/schedules/rooms")
public class RoomScheduleController {
    private final RoomScheduleService schedules;
    public RoomScheduleController(RoomScheduleService schedules){this.schedules=schedules;}
    @GetMapping("/{roomId}")
    public ApiResponse<RoomScheduleService.View> get(@PathVariable String roomId,@RequestHeader("X-User-Role") String role){
        requireAdmin(role);return ApiResponse.success(schedules.get(roomId));
    }
    @PutMapping("/{roomId}")
    public ApiResponse<RoomScheduleService.View> save(@PathVariable String roomId,@RequestBody RoomScheduleService.Update request,
            @RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){
        requireAdmin(role);return ApiResponse.success(schedules.save(roomId,request,actor));
    }
    private static void requireAdmin(String role){if(!"ADMIN".equals(role))throw new BusinessException(403,"Chỉ ADMIN được cấu hình lịch khám.");}
}
