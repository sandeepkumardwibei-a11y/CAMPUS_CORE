package com.campuscore.dto;
 
import com.campuscore.entity.User;
import com.campuscore.entity.Notification.NotificationCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
import java.time.LocalDateTime;
 
public class NotificationDto {
 
    // 1. Existing class for REST API Outputs (Do not change)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long notificationId;
        private Long userId;
        private String message;
        private String senderName;
        private String category;
        private String status;
        private LocalDateTime createdAt;
    }
 
    // 2. NEW class added inside to carry automated background messages
    @Data
    @AllArgsConstructor
    public static class Event {
        private final User user;
        private final String message;
        private final NotificationCategory category;
    }
}