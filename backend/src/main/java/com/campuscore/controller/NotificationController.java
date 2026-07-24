package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.NotificationDto;
import com.campuscore.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<NotificationDto.Response>> sendNotification(
            @RequestParam Long userId,
            @RequestParam String message,
            @RequestParam String category) {
        // Log trace at request entry point using safe parameter
        log.info("Processing sendNotification endpoint request for userId: {}", userId);
        
        NotificationDto.Response response = notificationService.sendNotification(userId, message, category);
        
        // Log trace at successful response point
        log.info("Successfully processed sendNotification endpoint request for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Notification sent successfully"));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId==authentication.principal.userId")
    public ResponseEntity<ApiResponse<Page<NotificationDto.Response>>> getUserNotifications(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getUserNotifications endpoint request for userId: {}", userId);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationDto.Response> response = notificationService.getUserNotifications(userId, pageable);
        
        // Log trace at successful response point
        log.info("Successfully processed getUserNotifications endpoint request for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched user notifications"));
    }

    @GetMapping("/user/{userId}/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(@PathVariable Long userId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getUnreadCount endpoint request for userId: {}", userId);
        
        long count = notificationService.getUnreadCount(userId);
        
        // Log trace at successful response point
        log.info("Successfully processed getUnreadCount endpoint request for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success(count, "Fetched unread notification count"));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasRole('ADMIN') or @notificationService.getUserIdByNotificationId(#id)==authentication.principal.userId")
    public ResponseEntity<ApiResponse<NotificationDto.Response>> markRead(@PathVariable Long id) {
        // Log trace at request entry point using safe path variable
        log.info("Processing markRead endpoint request for notificationId: {}", id);
        
        NotificationDto.Response response = notificationService.markRead(id);
        
        // Log trace at successful response point
        log.info("Successfully processed markRead endpoint request for notificationId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Notification marked as read"));
    }

    @PutMapping("/user/{userId}/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(@PathVariable Long userId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing markAllRead endpoint request for userId: {}", userId);
        
        notificationService.markAllRead(userId);
        
        // Log trace at successful response point
        log.info("Successfully processed markAllRead endpoint request for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success(null, "All notifications marked as read"));
    }
}