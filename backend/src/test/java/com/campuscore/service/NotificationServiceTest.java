package com.campuscore.service;

import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.Notification;
import com.campuscore.entity.User;
import com.campuscore.exception.NotificationException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.NotificationRepository;
import com.campuscore.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private NotificationService notificationService;

    private User recipientUser;
    private User senderUser;
    private Notification notification1;
    private Notification notification2;

    @BeforeEach
    void setUp() {
        // Setup Security Context (Simulating senderUser as the logged-in user)
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin@campus.com");

        senderUser = User.builder()
                .userId(99L)
                .email("admin@campus.com")
                .name("Admin User")
                .role(User.Role.ADMIN)
                .build();

        // The user receiving the notifications
        recipientUser = User.builder()
                .userId(1L)
                .email("student@campus.com")
                .name("Jane Student")
                .role(User.Role.STUDENT)
                .build();

        // Setup Notifications
        notification1 = Notification.builder()
                .notificationId(101L)
                .user(recipientUser)
                .message("Test Message 1")
                .senderName("Admin User")
                .category(Notification.NotificationCategory.ACADEMIC)
                .status(Notification.NotificationStatus.UNREAD)
                .createdAt(LocalDateTime.now())
                .build();

        notification2 = Notification.builder()
                .notificationId(102L)
                .user(recipientUser)
                .message("Test Message 2")
                .senderName("Admin User")
                .category(Notification.NotificationCategory.ACCOUNTS)
                .status(Notification.NotificationStatus.UNREAD)
                .createdAt(LocalDateTime.now())
                .build();

        // Mock Repository behaviors
        when(userRepository.findByEmail("admin@campus.com")).thenReturn(Optional.of(senderUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(recipientUser));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────
    // 1. SEND NOTIFICATION TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void sendNotification_Success() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setNotificationId(100L);
            n.setCreatedAt(LocalDateTime.now());
            return n;
        });

        // Passing parameters directly instead of using a Request object
        NotificationDto.Response response = notificationService.sendNotification(1L, "New Grade Posted", "ACADEMIC");

        assertNotNull(response);
        assertEquals(100L, response.getNotificationId());
        assertEquals("New Grade Posted", response.getMessage());
        assertEquals(Notification.NotificationStatus.UNREAD.name(), response.getStatus());

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void sendNotification_ThrowsException_WhenMessageIsEmpty() {
        assertThrows(NotificationException.class,
                () -> notificationService.sendNotification(1L, "   ", "ACADEMIC"));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendNotification_ThrowsException_WhenRecipientNotFound() {
        when(userRepository.findById(500L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.sendNotification(500L, "Hello", "INFO"));
    }

    @Test
    void sendNotification_ThrowsException_WhenSendingToSelf() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(senderUser));

        assertThrows(NotificationException.class,
                () -> notificationService.sendNotification(99L, "Note to self", "INFO"));
    }

    // ─────────────────────────────────────────────────────────
    // 2. READ / MARK READ TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void markRead_Success() {
        when(notificationRepository.findById(101L)).thenReturn(Optional.of(notification1));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDto.Response response = notificationService.markRead(101L);

        assertNotNull(response);
        assertEquals(Notification.NotificationStatus.READ.name(), response.getStatus());
        verify(notificationRepository, times(1)).save(notification1);
    }

    @Test
    void markRead_ThrowsException_WhenNotFound() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> notificationService.markRead(999L));
    }

    @Test
    void markAllRead_Success() {
        // FIXED: Spring Data @Modifying queries return an int/Integer of rows affected, not void.
        // We mock it to return a generic integer (e.g., 2 rows updated) to avoid the MockitoException.
        when(notificationRepository.markAllReadByUserId(1L)).thenReturn(2);

        notificationService.markAllRead(1L);

        verify(notificationRepository, times(1)).markAllReadByUserId(1L);
    }

    // ─────────────────────────────────────────────────────────
    // 3. FETCH & UTILITY TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getUserNotifications_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> pagedResponse = new PageImpl<>(List.of(notification1, notification2));

        when(notificationRepository.findByUserUserIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(pagedResponse);

        Page<NotificationDto.Response> responses = notificationService.getUserNotifications(1L, pageable);

        assertNotNull(responses);
        assertEquals(2, responses.getContent().size());
    }

    @Test
    void getUnreadCount_Success() {
        when(notificationRepository.countByUserUserIdAndStatus(1L, Notification.NotificationStatus.UNREAD))
                .thenReturn(5L);

        long count = notificationService.getUnreadCount(1L);
        assertEquals(5L, count);
    }

    @Test
    void getUserIdByNotificationId_Success() {
        when(notificationRepository.findById(101L)).thenReturn(Optional.of(notification1));

        Long userId = notificationService.getUserIdByNotificationId(101L);
        assertEquals(1L, userId);
    }

    // ─────────────────────────────────────────────────────────
    // 4. EVENT HANDLER / AUTOMATIC NOTIFICATION TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void handleAutomaticEvent_Success() {
        NotificationDto.Event event = new NotificationDto.Event(
                recipientUser,
                "Automatic alert: Fee generated",
                Notification.NotificationCategory.ACCOUNTS
        );

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            n.setNotificationId(200L);
            return n;
        });

        assertDoesNotThrow(() -> notificationService.handleAutomaticEvent(event));

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void handleAutomaticEvent_SkipsWhenUserIsNull() {
        NotificationDto.Event event = new NotificationDto.Event(
                null,
                "System maintenance tomorrow",
                Notification.NotificationCategory.SYSTEM
        );

        assertDoesNotThrow(() -> notificationService.handleAutomaticEvent(event));

        verify(notificationRepository, never()).save(any(Notification.class));
    }
}