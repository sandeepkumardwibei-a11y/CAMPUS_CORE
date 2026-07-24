package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "scholarship")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Scholarship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scholarship_id")
    private Long scholarshipId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "scholarship_name", nullable = false, length = 200)
    private String scholarshipName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "disbursed_date")
    private LocalDate disbursedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ScholarshipStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = ScholarshipStatus.APPROVED;
    }

    public enum SourceType { GOVERNMENT, INSTITUTIONAL, EXTERNAL }
    public enum ScholarshipStatus { APPROVED, DISBURSED, REVOKED }
}
