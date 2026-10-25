package com.careflow.queue.controller;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.service.QueueRoomConfigurationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/queues/rooms")
public class RoomQueuePolicyController {
    private final QueueRoomConfigurationService policies;
    public RoomQueuePolicyController(QueueRoomConfigurationService policies){this.policies=policies;}
    @GetMapping("/{roomId}/config")
    public ApiResponse<QueueRoomConfigurationService.View> get(@PathVariable String roomId,@RequestHeader("X-User-Role") String role){
        requireAdmin(role);return ApiResponse.success(policies.get(roomId));
    }
    @PutMapping("/{roomId}/config")
    public ApiResponse<QueueRoomConfigurationService.View> save(@PathVariable String roomId,@Valid @RequestBody QueueRoomConfigurationService.Policy body,@RequestHeader("X-User-Id") UUID actor,@RequestHeader("X-User-Role") String role){
        requireAdmin(role);return ApiResponse.success(policies.save(roomId,body,actor));
    }
    private static void requireAdmin(String role){if(!"ADMIN".equals(role))throw new BusinessException(403,"Chỉ ADMIN được cấu hình phòng.");}
}
