package com.careflow.notification.controller;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.notification.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class NotificationHistoryController {
    private final NotificationRepository notifications;
    public NotificationHistoryController(NotificationRepository notifications) { this.notifications = notifications; }
    @GetMapping("/api/notifications/history")
    public ApiResponse<List<NotificationResponse>> history(@RequestHeader("X-User-Id") UUID user,
            @RequestParam UUID before, @RequestParam(defaultValue="50") int limit) {
        var cursor = notifications.findByIdAndRecipientUserId(before, user)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", before));
        return ApiResponse.success(notifications.findOlder(user, cursor.getCreatedAt(), cursor.getId(),
            PageRequest.of(0, Math.max(1, Math.min(100, limit)))).stream().map(n -> new NotificationResponse(
            n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getActionType() == null ? null :
            new NotificationActionResponse(n.getActionType(), n.getResourceId()), n.getStatus(), n.getCreatedAt(), n.getReadAt())).toList());
    }
}
