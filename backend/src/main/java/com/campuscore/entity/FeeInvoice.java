package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_invoice",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "academic_year", "semester"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FeeInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    private Long invoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(nullable = false)
    private Integer semester;

    @Column(name = "tuition_fee", precision = 12, scale = 2)
    private BigDecimal tuitionFee = BigDecimal.ZERO;

    @Column(name = "library_fee", precision = 12, scale = 2)
    private BigDecimal libraryFee = BigDecimal.ZERO;

    @Column(name = "lab_fee", precision = 12, scale = 2)
    private BigDecimal labFee = BigDecimal.ZERO;

    @Column(name = "activity_fee", precision = 12, scale = 2)
    private BigDecimal activityFee = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "scholarship_adjusted", precision = 12, scale = 2)
    private BigDecimal scholarshipAdjusted = BigDecimal.ZERO;

    @Column(name = "net_payable", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPayable;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = InvoiceStatus.GENERATED;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum InvoiceStatus { GENERATED, PAID, PARTIALLY_PAID, OVERDUE, WAIVED }
}