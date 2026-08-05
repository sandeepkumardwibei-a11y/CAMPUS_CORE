package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "course")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "course_name", nullable = false, length = 200)
    private String courseName;

    @Column(name = "course_code", nullable = false, unique = true, length = 20)
    private String courseCode;

    // Legacy single-program link kept for backward compatibility / primary program.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = true) 
    private Program program;

    // MANY-TO-MANY: a course can belong to multiple programs, and a program can
    // have multiple courses. Stored as a set of linked program IDs.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "course_programs", joinColumns = @JoinColumn(name = "course_id"))
    @Column(name = "program_id")
    @Builder.Default
    private java.util.List<Long> programIds = new java.util.ArrayList<>();

    // 🎯 FIX: Semesters are assigned when linked to a curriculum/program, so make it nullable initially
    @Column(nullable = true)
    private Integer semester;

    @Column(nullable = false)
    private Integer credits;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private User faculty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CourseStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = CourseStatus.ACTIVE;
    }

    public enum CourseStatus { ACTIVE, INACTIVE }
}