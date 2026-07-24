package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.apache.http.auth.AUTH;

@Entity
@Table(name = "notification")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    // Name of the user who sent this notification (null for automatic/system notifications).
    @Column(name = "sender_name", length = 150)
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = NotificationStatus.UNREAD;
    }

    public enum NotificationCategory {
        ADMISSIONS, ATTENDANCE, EXAM, ACCOUNTS, HOSTEL, ACADEMIC, SYSTEM, AUTH,
        INFO, ALERT,COURSE,DEPARTMENT, PROGRAM
    }

    public enum NotificationStatus { UNREAD, READ, DISMISSED }
}
