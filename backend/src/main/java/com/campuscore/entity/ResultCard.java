package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "result_card",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id","academic_year","semester"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResultCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long resultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(nullable = false)
    private Integer semester;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal sgpa = BigDecimal.ZERO;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal cgpa = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer backlogs = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ResultStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = ResultStatus.DRAFT;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum ResultStatus { DRAFT, PUBLISHED, WITHHELD }
}
