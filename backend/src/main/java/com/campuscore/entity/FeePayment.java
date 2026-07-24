package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_payment")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FeePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private FeeInvoice invoice;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMode mode;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Column(name = "receipt_number", unique = true, length = 100)
    private String receiptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentStatus status;

    @Column(name = "proof_path")
    private String proofPath;

    @Column(name = "cash_breakdown_note", length = 500)
    private String cashBreakdownNote;

    @Column(name = "verification_reason", length = 500)
    private String verificationReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = PaymentStatus.RECEIVED;
        if (paymentDate == null) paymentDate = LocalDate.now();
    }

    public enum PaymentMode { NET_BANKING, NETBANKING, ONLINE, CARD, UPI, DD, CASH, BANK_TRANSFER }
    public enum PaymentStatus { RECEIVED, REVERSED, PENDING_VERIFICATION, REJECTED }
}
