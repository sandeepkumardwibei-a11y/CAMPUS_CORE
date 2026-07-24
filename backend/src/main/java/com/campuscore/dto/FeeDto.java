package com.campuscore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

public class FeeDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InvoiceCreateRequest {
        @NotNull private Long studentId;
        @NotBlank private String academicYear;
        @NotNull private Integer semester;
        private BigDecimal tuitionFee;
        private BigDecimal hostelFee;
        private BigDecimal libraryFee;
        private BigDecimal labFee;
        private BigDecimal activityFee;
        private BigDecimal scholarshipAdjusted;
        @NotNull private LocalDate dueDate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InvoiceResponse {
        private Long invoiceId;
        private Long studentId;
        private String studentName;
        private String academicYear;
        private Integer semester;
        private BigDecimal tuitionFee;
        private BigDecimal hostelFee;
        private BigDecimal libraryFee;
        private BigDecimal labFee;
        private BigDecimal activityFee;
        private BigDecimal totalAmount;
        private BigDecimal scholarshipAdjusted;
        private BigDecimal netPayable;
        private LocalDate dueDate;
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PaymentRequest {
        @NotNull private Long invoiceId;
        @NotNull private BigDecimal paidAmount;
        @NotBlank private String mode;
        private String referenceNo;
        private String cashBreakdownNote;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PaymentResponse {
        private Long paymentId;
        private Long invoiceId;
        private Long studentId;
        private String studentName;
        private BigDecimal paidAmount;
        private LocalDate paymentDate;
        private String mode;
        private String referenceNo;
        private String receiptNumber;
        private String status;
        private boolean hasProof;
        private String cashBreakdownNote;
        private String verificationReason;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProofSubmitRequest {
        @NotNull private Long invoiceId;
        @NotNull private BigDecimal paidAmount;
        @NotBlank private String mode; // DD or BANK_TRANSFER
        private String referenceNo;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class VerifyRequest {
        private String reason;
    }
}
