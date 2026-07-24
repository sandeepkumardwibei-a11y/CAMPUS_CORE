package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hostel_application")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HostelApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private HostelRoom.RoomType roomType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "hostel_fee")
    private Double hostelFee;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus;

    @Column(name = "application_date", nullable = false)
    private LocalDate applicationDate;

    @Column(name = "approved_by")
    private Long hostelAdminId;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (applicationDate == null) {
            applicationDate = LocalDate.now();
        }

        if (status == null) {
            status = ApplicationStatus.PENDING;
        }

        if (paymentStatus == null) {
            paymentStatus = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ApplicationStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}