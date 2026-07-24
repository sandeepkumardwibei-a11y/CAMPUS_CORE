package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_summary",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id","course_id","semester","academic_year"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "summary_id")
    private Long summaryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private Integer semester;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "total_lectures", nullable = false)
    private Integer totalLectures = 0;

    @Column(name = "attended_lectures", nullable = false)
    private Integer attendedLectures = 0;

    @Column(name = "attendance_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal attendancePercent = BigDecimal.ZERO;

    @Column(name = "shortage_flag", nullable = false)
    private Boolean shortageFlag = false;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @PrePersist @PreUpdate
    protected void onSave() { lastUpdated = LocalDateTime.now(); }
}
