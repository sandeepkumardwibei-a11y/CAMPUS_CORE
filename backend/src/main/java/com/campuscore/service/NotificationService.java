package com.campuscore.service;

import com.campuscore.dto.NotificationDto; // Housed our .Event inner class here
import com.campuscore.entity.Notification;
import com.campuscore.entity.User;
import com.campuscore.exception.NotificationException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.NotificationRepository;
import com.campuscore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // ==========================================
    // 1. AUTOMATIC NOTIFICATION TRIGGER (EVENT LISTENER)
    // ==========================================
    // Runs AFTER the publishing transaction commits (fallbackExecution=true lets it also
    // run when there is no active transaction). This guarantees the referenced user row is
    // already committed, so the notification insert never waits on / deadlocks against the
    // caller's row lock (previously caused PessimisticLockingFailureException on register).
    @Async // Background thread so the core request stays fast
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAutomaticEvent(NotificationDto.Event event) {
        // Guard: some events (e.g. system-wide structural updates like a new department)
        // carry no specific target user. The notification table requires a non-null user,
        // so we cannot persist a per-user row for these. Log and skip instead of throwing
        // a NullPointerException that would roll back the caller's transaction.
        if (event.getUser() == null) {
            log.info("Received system-wide notification event with no target user; logging only. Message: {}", event.getMessage());
            return;
        }

        log.info("Processing background automatic notification event for User ID: {}", event.getUser().getUserId());
        // Automatically creates and saves the notification in the background
        Notification notification = Notification.builder()
                .user(event.getUser())
                .message(event.getMessage())
                .category(event.getCategory())
                // 🎯 FIXED: Removed the .type() reference entirely since it doesn't exist in your entity
                .status(Notification.NotificationStatus.UNREAD)
                .build();

        notificationRepository.save(notification);
        log.info("Auto-Notification logged for User ID: {}", event.getUser().getUserId());
    }

    // ==========================================
    // 2. MANUAL NOTIFICATION TRIGGER (ADMIN API ACCESS)
    // ==========================================
    @Transactional
    public NotificationDto.Response sendNotification(Long userId, String message, String category) {
        log.info("Processing manual notification dispatch request to target user ID: {}", userId);

        // Validation: reject empty / blank messages.
        if (message == null || message.trim().isEmpty()) {
            throw new NotificationException("Cannot send an empty notification. Please enter a message.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Manual dispatch failed: User not found with ID: {}", userId);
                    return new ResourceNotFoundException("User", "id", userId);
                });

        // Resolve the sender (the currently authenticated user) so the recipient
        // can see who sent it. Falls back gracefully if there is no authentication.
        String senderName = resolveCurrentUserName();

        // Validation: a user cannot send a notification to themselves.
        Long senderId = resolveCurrentUserId();
        if (senderId != null && senderId.equals(userId)) {
            throw new NotificationException("You cannot send a notification to yourself.");
        }

        Notification notification = Notification.builder()
                .user(user)
                .message(message.trim())
                .senderName(senderName)
                .category(parseCategory(category))
                // 🎯 FIXED: Removed the .type() reference entirely since it doesn't exist in your entity
                .status(Notification.NotificationStatus.UNREAD)
                .build();

        notificationRepository.save(notification);
        log.info("Manual notification successfully dispatched and saved with ID: {}", notification.getNotificationId());
        return toResponse(notification);
    }

    /** Returns the userId of the currently authenticated user, or null if unavailable. */
    private Long resolveCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            Object principal = auth.getPrincipal();
            if (principal instanceof User u) {
                return u.getUserId();
            }
            if (auth.getName() != null) {
                return userRepository.findByEmail(auth.getName())
                        .map(User::getUserId)
                        .orElse(null);
            }
            return null;
        } catch (Exception ex) {
            log.debug("Could not resolve current sender id: {}", ex.getMessage());
            return null;
        }
    }

    /** Returns the display name of the currently authenticated user, or null if unavailable. */
    private String resolveCurrentUserName() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;

            // The principal is the User entity itself (User implements UserDetails),
            // so we can read the sender's name directly — no DB round-trip needed.
            Object principal = auth.getPrincipal();
            if (principal instanceof User u) {
                return u.getName();
            }

            // Fallback: resolve by the authenticated username (email).
            if (auth.getName() != null) {
                return userRepository.findByEmail(auth.getName())
                        .map(User::getName)
                        .orElse(auth.getName());
            }
            return null;
        } catch (Exception ex) {
            log.debug("Could not resolve current sender name: {}", ex.getMessage());
            return null;
        }
    }

    // ==========================================
    // CORE SUPPORTING METHODS & UTILITIES
    // ==========================================
    private Notification.NotificationCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            log.debug("No notification category provided. Defaulting to SYSTEM category mapping.");
            return Notification.NotificationCategory.SYSTEM;
        }
        try {
            return Notification.NotificationCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.error("Failed to parse incoming notification category metadata payload value: {}", category);
            throw new NotificationException("Invalid notification category: " + category);
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto.Response> getUserNotifications(Long userId, Pageable pageable) {
        log.debug("Fetching paginated notification history record page for user ID: {}", userId);
        return notificationRepository.findByUserUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        log.debug("Calculating cumulative unread count verification total metrics for user ID: {}", userId);
        return notificationRepository.countByUserUserIdAndStatus(userId, Notification.NotificationStatus.UNREAD);
    }

    @Transactional(readOnly = true)
    public Long getUserIdByNotificationId(Long notificationId) {
        log.debug("Resolving owning user ID for notification ID: {}", notificationId);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
        return notification.getUser().getUserId();
    }

    @Transactional
    public NotificationDto.Response markRead(Long notificationId) {
        log.info("Marking specific tracking notification record ID: {} status state to READ", notificationId);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> {
                    log.error("Failed to update status. Notification record ID: {} not found", notificationId);
                    return new ResourceNotFoundException("Notification", "id", notificationId);
                });
        notification.setStatus(Notification.NotificationStatus.READ);
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public void markAllRead(Long userId) {
        log.info("Executing batch modification query to clear all unread notification markers for user ID: {}", userId);
        notificationRepository.markAllReadByUserId(userId);
    }

    private NotificationDto.Response toResponse(Notification n) {
        return NotificationDto.Response.builder()
                .notificationId(n.getNotificationId())
                .userId(n.getUser().getUserId())
                .message(n.getMessage())
                .senderName(n.getSenderName())
                .category(n.getCategory().name())
                .status(n.getStatus().name())
                .createdAt(n.getCreatedAt())
                .build();
    }
}