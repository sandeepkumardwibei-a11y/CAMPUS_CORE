package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "academic_standing",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id","academic_year","semester"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcademicStanding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "standing_id")
    private Long standingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(nullable = false)
    private Integer semester;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal cgpa = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StandingStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = StandingStatus.GOOD;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum StandingStatus { GOOD, PROBATION, DETAINED, EXPELLED }
}
