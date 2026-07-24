package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.FeeDto;
import com.campuscore.service.FeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@RestController
@RequestMapping("/fees")
@RequiredArgsConstructor
public class FeeController {

    private final FeeService feeService;

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTS')")
    public ResponseEntity<ApiResponse<FeeDto.InvoiceResponse>> generateInvoice(
            @Valid @RequestBody FeeDto.InvoiceCreateRequest request) {
        // Log trace at request entry point
        log.info("Processing generateInvoice endpoint request");
        
        FeeDto.InvoiceResponse response = feeService.generateInvoice(request);
        
        // Log trace at successful response point
        log.info("Successfully processed generateInvoice endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Fee invoice generated successfully"));
    }

    @PostMapping("/payments")
    public ResponseEntity<ApiResponse<FeeDto.PaymentResponse>> recordPayment(
            @Valid @RequestBody FeeDto.PaymentRequest request) {
        // Log trace at request entry point
        log.info("Processing recordPayment endpoint request");
        
        FeeDto.PaymentResponse response = feeService.recordPayment(request);
        
        // Log trace at successful response point
        log.info("Successfully processed recordPayment endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Payment recorded successfully"));
    }

    @GetMapping("/student/{studentId}/invoices")
    public ResponseEntity<ApiResponse<List<FeeDto.InvoiceResponse>>> getStudentInvoices(@PathVariable Long studentId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getStudentInvoices endpoint request for studentId: {}", studentId);
        
        List<FeeDto.InvoiceResponse> response = feeService.getStudentInvoices(studentId);
        
        // Log trace at successful response point
        log.info("Successfully processed getStudentInvoices endpoint request for studentId: {}", studentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched student fee invoices"));
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    public ResponseEntity<ApiResponse<List<FeeDto.PaymentResponse>>> getInvoicePayments(@PathVariable Long invoiceId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getInvoicePayments endpoint request for invoiceId: {}", invoiceId);
        
        List<FeeDto.PaymentResponse> response = feeService.getInvoicePayments(invoiceId);
        
        // Log trace at successful response point
        log.info("Successfully processed getInvoicePayments endpoint request for invoiceId: {}", invoiceId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched payments for the invoice"));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTS')")
    public ResponseEntity<ApiResponse<Page<FeeDto.InvoiceResponse>>> getInvoices(
            @RequestParam String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Log trace at request entry point using safe parameters
        log.info("Processing getInvoices endpoint request for status: {}", status);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<FeeDto.InvoiceResponse> response = feeService.getInvoicesByStatus(status, pageable);
        
        // Log trace at successful response point
        log.info("Successfully processed getInvoices endpoint request for status: {}", status);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched invoices by status"));
    }

    // ===================================================================
    // STUDENT SELF-SERVICE: bank transfer / DD proof-of-payment upload
    // ===================================================================
    @PostMapping(value = "/payments/proof", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<FeeDto.PaymentResponse>> submitPaymentProof(
            @RequestParam Long invoiceId,
            @RequestParam BigDecimal paidAmount,
            @RequestParam String mode,
            @RequestParam(required = false) String referenceNo,
            @RequestParam("file") MultipartFile file) {
        log.info("Processing submitPaymentProof endpoint request for invoiceId: {}", invoiceId);
        FeeDto.PaymentResponse response = feeService.submitPaymentProof(invoiceId, paidAmount, mode, referenceNo, file);
        log.info("Successfully processed submitPaymentProof endpoint request for invoiceId: {}", invoiceId);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment proof submitted. Awaiting verification."));
    }

    // ACCOUNTS/ADMIN: list every payment awaiting proof verification
    @GetMapping("/payments/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTS')")
    public ResponseEntity<ApiResponse<List<FeeDto.PaymentResponse>>> getPendingProofPayments() {
        log.info("Processing getPendingProofPayments endpoint request");
        List<FeeDto.PaymentResponse> response = feeService.getPendingProofPayments();
        log.info("Successfully processed getPendingProofPayments endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched payments awaiting verification"));
    }

    // ACCOUNTS/ADMIN: confirm a proof-based payment after checking the document
    @PutMapping("/payments/{paymentId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTS')")
    public ResponseEntity<ApiResponse<FeeDto.PaymentResponse>> confirmProofPayment(@PathVariable Long paymentId) {
        log.info("Processing confirmProofPayment endpoint request for paymentId: {}", paymentId);
        FeeDto.PaymentResponse response = feeService.confirmProofPayment(paymentId);
        log.info("Successfully processed confirmProofPayment endpoint request for paymentId: {}", paymentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment verified and recorded"));
    }

    // ACCOUNTS/ADMIN: reject a proof-based payment
    @PutMapping("/payments/{paymentId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTS')")
    public ResponseEntity<ApiResponse<FeeDto.PaymentResponse>> rejectProofPayment(
            @PathVariable Long paymentId,
            @RequestBody(required = false) FeeDto.VerifyRequest request) {
        log.info("Processing rejectProofPayment endpoint request for paymentId: {}", paymentId);
        String reason = request != null ? request.getReason() : null;
        FeeDto.PaymentResponse response = feeService.rejectProofPayment(paymentId, reason);
        log.info("Successfully processed rejectProofPayment endpoint request for paymentId: {}", paymentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment proof rejected"));
    }

    // View/download the uploaded proof image (owner student, or ADMIN/ACCOUNTS)
    @GetMapping("/payments/{paymentId}/proof")
    public ResponseEntity<?> viewProof(@PathVariable Long paymentId) {
        try {
            byte[] bytes = feeService.downloadProof(paymentId);
            String contentType = feeService.resolveProofContentType(paymentId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"proof\"")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }
}