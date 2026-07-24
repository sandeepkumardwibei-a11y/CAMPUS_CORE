package com.campuscore.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "offer_letter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // 👈 Protects against proxy mapping faults
public class OfferLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offer_id")
    private Long offerId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    @JsonIgnore // 👈 Breaks cyclic serialization loops entirely
    private AdmissionApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Program program;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "joining_deadline", nullable = false)
    private LocalDate joiningDeadline;

    @Builder.Default
    @Column(name = "scholarship_amount", precision = 12, scale = 2)
    private BigDecimal scholarshipAmount = BigDecimal.ZERO;

    @Column(name = "fee_details_ref", length = 255)
    private String feeDetailsRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OfferStatus status;

    @Column(name = "formal_letter_content", length = 2000)
    private String formalLetterContent;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = OfferStatus.ISSUED;
        if (issueDate == null) issueDate = LocalDate.now();
    }

    public enum OfferStatus {
        ISSUED,
        ACCEPTED,
        LAPSED,
        REVOKED
    }
}
