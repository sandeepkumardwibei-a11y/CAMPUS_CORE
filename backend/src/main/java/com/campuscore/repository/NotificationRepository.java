package com.campuscore.repository;
 
import com.campuscore.entity.Notification;
import com.campuscore.entity.Notification.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
 
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByUserUserIdAndStatus(Long userId, NotificationStatus status);
 
    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ' WHERE n.user.userId = :userId AND n.status = 'UNREAD'")
    int markAllReadByUserId(Long userId);
}
 