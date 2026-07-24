package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hostel_allotment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id","academic_year"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HostelAllotment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allotment_id")
    private Long allotmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private HostelRoom room;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(name = "checkout_date")
    private LocalDate checkoutDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AllotmentStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = AllotmentStatus.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum AllotmentStatus { ACTIVE, VACATED_EARLY, COMPLETED }
}