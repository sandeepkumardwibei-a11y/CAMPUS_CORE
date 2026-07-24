package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grade_record",
       uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id","student_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GradeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grade_id")
    private Long gradeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamSchedule exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "marks_obtained", nullable = false, precision = 6, scale = 2)
    private BigDecimal marksObtained = BigDecimal.ZERO;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks = new BigDecimal("100.00");

    @Column(length = 5)
    private String grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id")
    private User submittedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GradeStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = GradeStatus.DRAFT;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum GradeStatus { DRAFT, SUBMITTED, PUBLISHED, RE_EVALUATION_REQUESTED, REVISED }
}
