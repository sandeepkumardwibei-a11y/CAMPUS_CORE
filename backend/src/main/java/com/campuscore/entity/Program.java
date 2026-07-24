package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "program")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "program_id")
    private Long programId;

    @Column(name = "program_name", nullable = false, length = 200)
    private String programName;

    // One department can own many programs; a program belongs to exactly one department.
    @Column(name = "department_id")
    private Long departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Level level;

    @Column(name = "duration_years", nullable = false)
    private Integer durationYears;

    @Column(name = "total_seats", nullable = false)
    @Builder.Default
    private Integer totalSeats = 60;

    @Column(name = "minimum_percentage", nullable = false)
    private Double minimumPercentage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ProgramStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = ProgramStatus.ACTIVE;
        if (minimumPercentage == null) minimumPercentage = 60.0;
    }

    public enum Level { UG, PG, PHD, DIPLOMA }
    public enum ProgramStatus { ACTIVE, DISCONTINUED }
}