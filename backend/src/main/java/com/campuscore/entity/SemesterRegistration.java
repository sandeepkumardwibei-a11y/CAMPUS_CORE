package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "semester_registration")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SemesterRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registration_id")
    private Long registrationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(nullable = false)
    private Integer semester;

    @Column(name = "total_credits", nullable = false)
    private Integer totalCredits = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private RegistrationStatus status;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "registration_course",
        joinColumns        = @JoinColumn(name = "registration_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    @Builder.Default
    private Set<Course> courses = new HashSet<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = RegistrationStatus.REGISTERED;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum RegistrationStatus { REGISTERED, CONFIRMED, WITHDRAWN }
}
