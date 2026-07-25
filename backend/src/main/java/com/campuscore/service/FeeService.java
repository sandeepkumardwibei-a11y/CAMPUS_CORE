package com.campuscore.service;

import com.campuscore.dto.FeeDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.FeeInvoice;
import com.campuscore.entity.FeePayment;
import com.campuscore.entity.Notification.NotificationCategory;
import com.campuscore.entity.SemesterRegistration;
import com.campuscore.entity.User;
import com.campuscore.exception.FeeException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.FeeInvoiceRepository;
import com.campuscore.repository.FeePaymentRepository;
import com.campuscore.repository.SemesterRegistrationRepository;
import com.campuscore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Safe SLF4J logger import
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j // Plugs the SLF4J logging engine into this class automatically via Lombok
@Service
@RequiredArgsConstructor
public class FeeService {

    private final FeeInvoiceRepository invoiceRepository;
    private final FeePaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final SemesterRegistrationRepository semesterRegistrationRepository;
    private final ApplicationEventPublisher eventPublisher; // 🔔 Injected event publisher for automatic alerts
    private final FileStorageService fileStorageService;

    private void verifyStudentDataOwnership(Long studentUserId) {
        log.info("Verifying structural data ownership privileges for studentUserId: {}", studentUserId);
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername;

        if (principal instanceof UserDetails) {
            currentUsername = ((UserDetails) principal).getUsername();
        } else {
            currentUsername = principal.toString();
        }

        User currentUser = userRepository.findByEmail(currentUsername)
                .orElseThrow(() -> new FeeException("Access Denied: Invalid security execution context."));

        if (!currentUser.getUserId().equals(studentUserId) && 
            currentUser.getRole() != User.Role.ADMIN && 
            currentUser.getRole() != User.Role.ACCOUNTS) {
            throw new FeeException("Access Denied: You are not authorized to view or modify this financial profile.");
        }
    }

    @Transactional
    public FeeDto.InvoiceResponse generateInvoice(FeeDto.InvoiceCreateRequest request) {
        log.info("Entering generateInvoice sequence for studentId: {}, Term: {}, Sem: {}", 
                request.getStudentId(), request.getAcademicYear(), request.getSemester());

        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getStudentId()));

        if (student.getRole() != User.Role.STUDENT) {
            throw new FeeException("Invoice Generation Failed: The user ID " + request.getStudentId() + " is not a student account.");
        }

        List<SemesterRegistration> registrations = semesterRegistrationRepository
                .findByStudentUserIdAndAcademicYearAndSemester(
                        request.getStudentId(), request.getAcademicYear(), request.getSemester());

        boolean hasActiveRegistration = registrations.stream()
                .anyMatch(r -> r.getStatus() != SemesterRegistration.RegistrationStatus.WITHDRAWN);

        if (!hasActiveRegistration) {
            throw new FeeException("Invoice Generation Failed: Student " + request.getStudentId()
                    + " is not registered for Semester " + request.getSemester()
                    + " (" + request.getAcademicYear() + "). Please complete semester registration first.");
        }

        if (invoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(
                request.getStudentId(), request.getAcademicYear(), request.getSemester()).isPresent()) {
            throw new FeeException("Invoice already exists for this student and semester");
        }

        BigDecimal tuition = request.getTuitionFee() != null ? request.getTuitionFee() : BigDecimal.ZERO;
        BigDecimal library = request.getLibraryFee() != null ? request.getLibraryFee() : BigDecimal.ZERO;
        BigDecimal lab = request.getLabFee() != null ? request.getLabFee() : BigDecimal.ZERO;
        BigDecimal activity = request.getActivityFee() != null ? request.getActivityFee() : BigDecimal.ZERO;
        BigDecimal scholarship = request.getScholarshipAdjusted() != null ? request.getScholarshipAdjusted() : BigDecimal.ZERO;

        // Total excludes hostel fee entirely now
        BigDecimal total = tuition.add(library).add(lab).add(activity);
        BigDecimal net = total.subtract(scholarship);
        
        if (net.compareTo(BigDecimal.ZERO) < 0) net = BigDecimal.ZERO;

        FeeInvoice invoice = FeeInvoice.builder()
                .student(student)
                .academicYear(request.getAcademicYear())
                .semester(request.getSemester())
                .tuitionFee(tuition)
                .libraryFee(library)
                .labFee(lab)
                .activityFee(activity)
                .totalAmount(total)
                .scholarshipAdjusted(scholarship)
                .netPayable(net)
                .dueDate(request.getDueDate())
                .status(FeeInvoice.InvoiceStatus.GENERATED)
                .build();

        invoiceRepository.save(invoice);

        // 🔔 AUTOMATIC NOTIFICATION: Alert the student that a new fee statement has been billed
        String invoiceMessage = String.format(
                "Fee Invoice Generated: An invoice has been raised for Semester %d (%s). Net Payable: %s. Please clear your dues before the deadline on %s.",
                invoice.getSemester(),
                invoice.getAcademicYear(),
                invoice.getNetPayable().toString(),
                invoice.getDueDate().toString()
        );

        eventPublisher.publishEvent(new NotificationDto.Event(
                invoice.getStudent(),
                invoiceMessage,
                NotificationCategory.ACCOUNTS
        ));
        
        log.info("Successfully generated and saved new fee invoice record with ID: {}", invoice.getInvoiceId());
        return toInvoiceResponse(invoice);
    }

    @Transactional
    public FeeDto.PaymentResponse recordPayment(FeeDto.PaymentRequest request) {
        log.info("Entering recordPayment transactional sequence sequence for invoiceId: {}", request.getInvoiceId());
        
        String currentAuthenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User executingUser = userRepository.findByEmail(currentAuthenticatedEmail)
                .orElseThrow(() -> new FeeException("Access Denied: Unauthenticated session. Please log in."));

        FeeInvoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("FeeInvoice", "id", request.getInvoiceId()));

        verifyStudentDataOwnership(invoice.getStudent().getUserId());

        if (invoice.getStatus() == FeeInvoice.InvoiceStatus.PAID) {
            throw new FeeException("Invoice is already fully paid");
        }

        BigDecimal grossRemainingPayable = invoice.getNetPayable();

        if (request.getPaidAmount().compareTo(grossRemainingPayable) > 0) {
            throw new FeeException("Payment amount exceeds remaining payable dues: " + grossRemainingPayable);
        }

        FeePayment payment = FeePayment.builder()
                .invoice(invoice)
                .paidAmount(request.getPaidAmount())
                .paymentDate(LocalDate.now())
                .mode(FeePayment.PaymentMode.valueOf(request.getMode().toUpperCase()))
                .referenceNo(request.getReferenceNo())
                .receiptNumber("RCPT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .cashBreakdownNote(request.getCashBreakdownNote())
                .status(FeePayment.PaymentStatus.RECEIVED)
                .build();

        paymentRepository.save(payment);

        applyPaymentToInvoice(invoice, request.getPaidAmount());

        // 🔔 AUTOMATIC NOTIFICATION: Dispatch payment receipt status confirmation to the student profile
        String paymentMessage = String.format(
                "Payment Confirmed: We have successfully processed your payment of %s via %s. Receipt Number: %s. Your outstanding balance for this invoice is now %s (Status: %s).",
                payment.getPaidAmount().toString(),
                payment.getMode().name(),
                payment.getReceiptNumber(),
                invoice.getNetPayable().toString(),
                invoice.getStatus().name()
        );

        eventPublisher.publishEvent(new NotificationDto.Event(
                invoice.getStudent(),
                paymentMessage,
                NotificationCategory.ACCOUNTS
        ));
        
        log.info("Successfully calculated remaining liabilities and committed payment record with ID: {}", payment.getPaymentId());
        return toPaymentResponse(payment);
    }

    /** Applies a confirmed payment amount to an invoice's remaining balance and status. */
    private void applyPaymentToInvoice(FeeInvoice invoice, BigDecimal paidAmount) {
        BigDecimal remaining = invoice.getNetPayable().subtract(paidAmount);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        invoice.setNetPayable(remaining);
        invoice.setStatus(remaining.compareTo(BigDecimal.ZERO) == 0
                ? FeeInvoice.InvoiceStatus.PAID
                : FeeInvoice.InvoiceStatus.PARTIALLY_PAID);
        invoiceRepository.save(invoice);
    }

    /**
     * STUDENT SELF-SERVICE: Submit proof of a bank transfer / DD payment (an uploaded image/PDF).
     * This does NOT touch the invoice balance yet — it creates a PENDING_VERIFICATION record that
     * Accounts/Admin must review and confirm (or reject) before it counts towards the dues.
     */
    @Transactional
    public FeeDto.PaymentResponse submitPaymentProof(Long invoiceId, BigDecimal paidAmount, String mode, String referenceNo, MultipartFile proofFile) {
        log.info("Processing payment-proof submission for invoiceId: {}", invoiceId);

        FeeInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("FeeInvoice", "id", invoiceId));

        verifyStudentDataOwnership(invoice.getStudent().getUserId());

        if (invoice.getStatus() == FeeInvoice.InvoiceStatus.PAID) {
            throw new FeeException("Invoice is already fully paid");
        }
        FeePayment.PaymentMode parsedMode;
        try {
            parsedMode = FeePayment.PaymentMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new FeeException("Invalid payment mode: " + mode);
        }
        if (parsedMode != FeePayment.PaymentMode.DD && parsedMode != FeePayment.PaymentMode.BANK_TRANSFER) {
            throw new FeeException("Proof-of-payment upload is only used for DD or BANK_TRANSFER modes.");
        }

        String storedPath = fileStorageService.store("fee-proofs", String.valueOf(invoiceId), proofFile);

        FeePayment payment = FeePayment.builder()
                .invoice(invoice)
                .paidAmount(paidAmount)
                .paymentDate(LocalDate.now())
                .mode(parsedMode)
                .referenceNo(referenceNo)
                .proofPath(storedPath)
                .status(FeePayment.PaymentStatus.PENDING_VERIFICATION)
                .build();
        paymentRepository.save(payment);

        String message = String.format(
                "Payment Proof Submitted: Your %s payment proof of %s has been submitted and is awaiting verification by Accounts.",
                parsedMode.name(), paidAmount.toString());
        eventPublisher.publishEvent(new NotificationDto.Event(invoice.getStudent(), message, NotificationCategory.ACCOUNTS));

        log.info("Payment proof stored for invoiceId: {}, paymentId: {}", invoiceId, payment.getPaymentId());
        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<FeeDto.PaymentResponse> getPendingProofPayments() {
        log.debug("Fetching all payments awaiting proof verification");
        return paymentRepository.findByStatusOrderByCreatedAtDesc(FeePayment.PaymentStatus.PENDING_VERIFICATION)
                .stream().map(this::toPaymentResponse).collect(Collectors.toList());
    }

    /** ACCOUNTS/ADMIN: confirm a proof-based payment after checking the uploaded document. */
    @Transactional
    public FeeDto.PaymentResponse confirmProofPayment(Long paymentId) {
        FeePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("FeePayment", "id", paymentId));
        if (payment.getStatus() != FeePayment.PaymentStatus.PENDING_VERIFICATION) {
            throw new FeeException("Only payments awaiting verification can be confirmed.");
        }
        FeeInvoice invoice = payment.getInvoice();
        if (payment.getPaidAmount().compareTo(invoice.getNetPayable()) > 0) {
            throw new FeeException("Payment amount exceeds remaining payable dues: " + invoice.getNetPayable());
        }

        payment.setStatus(FeePayment.PaymentStatus.RECEIVED);
        payment.setReceiptNumber("RCPT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        paymentRepository.save(payment);

        applyPaymentToInvoice(invoice, payment.getPaidAmount());

        String message = String.format(
                "Payment Verified: Your %s payment of %s has been verified and recorded. Receipt Number: %s.",
                payment.getMode().name(), payment.getPaidAmount().toString(), payment.getReceiptNumber());
        eventPublisher.publishEvent(new NotificationDto.Event(invoice.getStudent(), message, NotificationCategory.ACCOUNTS));

        return toPaymentResponse(payment);
    }

    /** ACCOUNTS/ADMIN: reject a proof-based payment (e.g. proof doesn't match / invalid). */
    @Transactional
    public FeeDto.PaymentResponse rejectProofPayment(Long paymentId, String reason) {
        FeePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("FeePayment", "id", paymentId));
        if (payment.getStatus() != FeePayment.PaymentStatus.PENDING_VERIFICATION) {
            throw new FeeException("Only payments awaiting verification can be rejected.");
        }
        payment.setStatus(FeePayment.PaymentStatus.REJECTED);
        payment.setVerificationReason(reason != null && !reason.isBlank() ? reason : "No reason provided.");
        paymentRepository.save(payment);

        String message = String.format(
                "Payment Proof Rejected: Your submitted %s payment proof was rejected. Reason: %s. Please resubmit with a valid proof.",
                payment.getMode().name(), payment.getVerificationReason());
        eventPublisher.publishEvent(new NotificationDto.Event(payment.getInvoice().getStudent(), message, NotificationCategory.ACCOUNTS));

        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public byte[] downloadProof(Long paymentId) {
        FeePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("FeePayment", "id", paymentId));
        verifyStudentDataOwnership(payment.getInvoice().getStudent().getUserId());
        if (payment.getProofPath() == null) {
            throw new FeeException("No proof file was uploaded for this payment.");
        }
        return fileStorageService.read(payment.getProofPath());
    }

    public String resolveProofContentType(Long paymentId) {
        FeePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("FeePayment", "id", paymentId));
        return payment.getProofPath() == null ? "application/octet-stream" : fileStorageService.contentTypeFor(payment.getProofPath());
    }

    @Transactional(readOnly = true)
    public List<FeeDto.InvoiceResponse> getStudentInvoices(Long studentId) {
        log.info("Fetching all personal historical invoice balances for studentId: {}", studentId);
        verifyStudentDataOwnership(studentId);
        return invoiceRepository.findByStudentUserId(studentId).stream()
                .map(this::toInvoiceResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FeeDto.PaymentResponse> getInvoicePayments(Long invoiceId) {
        log.info("Fetching nested payment audit ledgers for target invoiceId: {}", invoiceId);
        FeeInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("FeeInvoice", "id", invoiceId));

        verifyStudentDataOwnership(invoice.getStudent().getUserId());

        return paymentRepository.findByInvoice_InvoiceId(invoiceId).stream()
                .map(this::toPaymentResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<FeeDto.InvoiceResponse> getInvoicesByStatus(String status, Pageable pageable) {
        log.info("Querying systemic accounting tables for invoices matching status criteria: {}", status);
        return invoiceRepository.findByStatus(FeeInvoice.InvoiceStatus.valueOf(status.toUpperCase()), pageable)
                .map(this::toInvoiceResponse);
    }

    private FeeDto.InvoiceResponse toInvoiceResponse(FeeInvoice i) {
        return FeeDto.InvoiceResponse.builder()
                .invoiceId(i.getInvoiceId())
                .studentId(i.getStudent().getUserId())
                .studentName(i.getStudent().getName())
                .academicYear(i.getAcademicYear())
                .semester(i.getSemester())
                .tuitionFee(i.getTuitionFee())
                .libraryFee(i.getLibraryFee())
                .labFee(i.getLabFee())
                .activityFee(i.getActivityFee())
                .totalAmount(i.getTotalAmount())
                .scholarshipAdjusted(i.getScholarshipAdjusted())
                .netPayable(i.getNetPayable())
                .dueDate(i.getDueDate())
                .status(i.getStatus().name())
                .build();
    }

    private FeeDto.PaymentResponse toPaymentResponse(FeePayment p) {
        return FeeDto.PaymentResponse.builder()
                .paymentId(p.getPaymentId())
                .invoiceId(p.getInvoice().getInvoiceId())
                .studentId(p.getInvoice().getStudent().getUserId())
                .studentName(p.getInvoice().getStudent().getName())
                .paidAmount(p.getPaidAmount())
                .paymentDate(p.getPaymentDate())
                .mode(p.getMode().name())
                .referenceNo(p.getReferenceNo())
                .receiptNumber(p.getReceiptNumber())
                .status(p.getStatus().name())
                .hasProof(p.getProofPath() != null)
                .cashBreakdownNote(p.getCashBreakdownNote())
                .verificationReason(p.getVerificationReason())
                .build();
    }
}