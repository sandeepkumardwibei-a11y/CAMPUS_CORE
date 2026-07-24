package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "exam_schedule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExamSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exam_id")
    private Long examId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private Integer semester;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 20)
    private ExamType examType;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_mins", nullable = false)
    private Integer durationMins = 180;

    @Column(length = 100)
    private String venue;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks = new BigDecimal("100.00");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ExamStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = ExamStatus.SCHEDULED;
    }

    public enum ExamType { INTERNAL, END_SEMESTER, PRACTICAL, SUPPLEMENTARY, MID}
    public enum ExamStatus { SCHEDULED, CONDUCTED, CANCELLED }
}
