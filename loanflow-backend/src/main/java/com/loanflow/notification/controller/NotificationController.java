package com.loanflow.notification.controller;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.notification.dto.MarkAllReadResponse;
import com.loanflow.notification.dto.NotificationResponse;
import com.loanflow.notification.dto.UnreadCountResponse;
import com.loanflow.notification.entity.NotificationStatus;
import com.loanflow.notification.entity.NotificationType;
import com.loanflow.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(notificationService.getMyNotifications(type, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getMyNotificationById(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getMyNotificationById(id));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        return ResponseEntity.ok(notificationService.getUnreadCount());
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<MarkAllReadResponse> markAllAsRead() {
        return ResponseEntity.ok(notificationService.markAllAsRead());
    }
}
