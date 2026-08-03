package com.careflow.notification.controller;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.notification.domain.NotificationStatus;
import com.careflow.notification.dto.NotificationResponse;
import com.careflow.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> inbox(
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(service.inbox(userId, status, limit));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId) {
        return ApiResponse.success(Map.of("unreadCount", service.unreadCount(userId)));
    }

    @PostMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId,
            @PathVariable UUID notificationId) {
        return ApiResponse.success("Đã đánh dấu thông báo là đã đọc",
                service.markRead(userId, notificationId));
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(
            @RequestHeader(AppConstants.HEADER_USER_ID) UUID userId) {
        return ApiResponse.success("Đã đọc tất cả thông báo",
                Map.of("updatedCount", service.markAllRead(userId)));
    }
}
