package com.careflow.notification.controller;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.notification.dto.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class NotificationDetailController {
    private final NotificationRepository notifications;
    public NotificationDetailController(NotificationRepository notifications){this.notifications=notifications;}
    @GetMapping("/api/notifications/{id}")
    public ApiResponse<NotificationResponse> get(@PathVariable UUID id,@RequestHeader("X-User-Id") UUID user){
        var n=notifications.findByIdAndRecipientUserId(id,user).orElseThrow(()->new ResourceNotFoundException("Notification","id",id));
        return ApiResponse.success(new NotificationResponse(n.getId(),n.getType(),n.getTitle(),n.getBody(),n.getActionType()==null?null:new NotificationActionResponse(n.getActionType(),n.getResourceId()),n.getStatus(),n.getCreatedAt(),n.getReadAt()));
    }
}
