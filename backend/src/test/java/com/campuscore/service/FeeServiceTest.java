package com.campuscore.service;

import com.campuscore.dto.FeeDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.FeeInvoice;
import com.campuscore.entity.FeePayment;
import com.campuscore.entity.SemesterRegistration;
import com.campuscore.entity.User;
import com.campuscore.exception.FeeException;
import com.campuscore.repository.FeeInvoiceRepository;
import com.campuscore.repository.FeePaymentRepository;
import com.campuscore.repository.SemesterRegistrationRepository;
import com.campuscore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeeServiceTest {

    @Mock
    private FeeInvoiceRepository invoiceRepository;

    @Mock
    private FeePaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SemesterRegistrationRepository semesterRegistrationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private FeeService feeService;

    private User sampleStudent;
    private User currentUserSession;
    private FeeInvoice sampleInvoice;
    private FeePayment samplePayment;
    private SemesterRegistration sampleRegistration;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        sampleStudent = new User();
        sampleStudent.setUserId(700L);
        sampleStudent.setName("Alice Green");
        sampleStudent.setEmail("alice@campuscore.com");
        sampleStudent.setRole(User.Role.STUDENT);

        currentUserSession = new User();
        currentUserSession.setUserId(700L);
        currentUserSession.setName("Alice Green");
        currentUserSession.setEmail("alice@campuscore.com");
        currentUserSession.setRole(User.Role.STUDENT);

        sampleRegistration = new SemesterRegistration();
        sampleRegistration.setStudent(sampleStudent);
        sampleRegistration.setAcademicYear("2026-27");
        sampleRegistration.setSemester(1);
        sampleRegistration.setStatus(SemesterRegistration.RegistrationStatus.REGISTERED);

        sampleInvoice = new FeeInvoice();
        sampleInvoice.setInvoiceId(800L);
        sampleInvoice.setStudent(sampleStudent);
        sampleInvoice.setAcademicYear("2026-27");
        sampleInvoice.setSemester(1);
        sampleInvoice.setTuitionFee(new BigDecimal("50000.00"));
        sampleInvoice.setLibraryFee(new BigDecimal("2000.00"));
        sampleInvoice.setLabFee(new BigDecimal("3000.00"));
        sampleInvoice.setActivityFee(new BigDecimal("1000.00"));
        sampleInvoice.setTotalAmount(new BigDecimal("56000.00"));
        sampleInvoice.setScholarshipAdjusted(new BigDecimal("6000.00"));
        sampleInvoice.setNetPayable(new BigDecimal("50000.00"));
        sampleInvoice.setDueDate(LocalDate.now().plusDays(30));
        sampleInvoice.setStatus(FeeInvoice.InvoiceStatus.GENERATED);

        samplePayment = new FeePayment();
        samplePayment.setPaymentId(900L);
        samplePayment.setInvoice(sampleInvoice);
        samplePayment.setPaidAmount(new BigDecimal("50000.00"));
        samplePayment.setPaymentDate(LocalDate.now());
        samplePayment.setMode(FeePayment.PaymentMode.UPI);
        samplePayment.setReferenceNo("TXN12345");
        samplePayment.setReceiptNumber("RCPT-MARK123");
        samplePayment.setStatus(FeePayment.PaymentStatus.RECEIVED);
    }

    private void mockSecurityContext(String username) {
        // 💡 FIX: Using lenient() so helper stubbing works seamlessly whether
        // getPrincipal() or getName() is called by the underlying service method.
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(username);
        lenient().when(authentication.getName()).thenReturn(username);
    }

    // ─────────────────────────────────────────────────────────
    // INVOICE GENERATION TEST CASES
    // ─────────────────────────────────────────────────────────

    @Test
    void generateInvoice_Success() {
        FeeDto.InvoiceCreateRequest request = new FeeDto.InvoiceCreateRequest();
        request.setStudentId(700L);
        request.setAcademicYear("2026-27");
        request.setSemester(1);
        request.setTuitionFee(new BigDecimal("50000.00"));
        request.setLibraryFee(new BigDecimal("2000.00"));
        request.setLabFee(new BigDecimal("3000.00"));
        request.setActivityFee(new BigDecimal("1000.00"));
        request.setScholarshipAdjusted(new BigDecimal("6000.00"));
        request.setDueDate(LocalDate.now().plusDays(30));

        when(userRepository.findById(700L)).thenReturn(Optional.of(sampleStudent));
        when(semesterRegistrationRepository.findByStudentUserIdAndAcademicYearAndSemester(700L, "2026-27", 1))
                .thenReturn(Collections.singletonList(sampleRegistration));
        when(invoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(700L, "2026-27", 1))
                .thenReturn(Optional.empty());

        when(invoiceRepository.save(any(FeeInvoice.class))).thenAnswer(invocation -> {
            FeeInvoice invoice = invocation.getArgument(0);
            invoice.setInvoiceId(800L);
            return invoice;
        });

        FeeDto.InvoiceResponse response = feeService.generateInvoice(request);

        assertNotNull(response);
        assertEquals(800L, response.getInvoiceId());
        assertEquals(0, response.getNetPayable().compareTo(new BigDecimal("50000.00")));
        assertEquals("GENERATED", response.getStatus());
        verify(invoiceRepository).save(any(FeeInvoice.class));
        verify(eventPublisher).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void generateInvoice_ThrowsException_WhenUserIsNotStudent() {
        FeeDto.InvoiceCreateRequest request = new FeeDto.InvoiceCreateRequest();
        request.setStudentId(700L);

        sampleStudent.setRole(User.Role.FACULTY);
        when(userRepository.findById(700L)).thenReturn(Optional.of(sampleStudent));

        FeeException ex = assertThrows(FeeException.class, () -> feeService.generateInvoice(request));
        assertTrue(ex.getMessage().contains("is not a student account"));
    }

    @Test
    void generateInvoice_ThrowsException_WhenNotRegisteredForSemester() {
        FeeDto.InvoiceCreateRequest request = new FeeDto.InvoiceCreateRequest();
        request.setStudentId(700L);
        request.setAcademicYear("2026-27");
        request.setSemester(1);

        when(userRepository.findById(700L)).thenReturn(Optional.of(sampleStudent));
        when(semesterRegistrationRepository.findByStudentUserIdAndAcademicYearAndSemester(700L, "2026-27", 1))
                .thenReturn(Collections.emptyList());

        FeeException ex = assertThrows(FeeException.class, () -> feeService.generateInvoice(request));
        assertTrue(ex.getMessage().contains("Please complete semester registration first"));
    }

    @Test
    void generateInvoice_ThrowsException_WhenInvoiceAlreadyExists() {
        FeeDto.InvoiceCreateRequest request = new FeeDto.InvoiceCreateRequest();
        request.setStudentId(700L);
        request.setAcademicYear("2026-27");
        request.setSemester(1);

        when(userRepository.findById(700L)).thenReturn(Optional.of(sampleStudent));
        when(semesterRegistrationRepository.findByStudentUserIdAndAcademicYearAndSemester(700L, "2026-27", 1))
                .thenReturn(Collections.singletonList(sampleRegistration));
        when(invoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(700L, "2026-27", 1))
                .thenReturn(Optional.of(sampleInvoice));

        FeeException ex = assertThrows(FeeException.class, () -> feeService.generateInvoice(request));
        assertTrue(ex.getMessage().contains("Invoice already exists"));
    }

    // ─────────────────────────────────────────────────────────
    // PAYMENT RECORDING TEST CASES
    // ─────────────────────────────────────────────────────────

    @Test
    void recordPayment_FullPaymentSuccess() {
        mockSecurityContext("alice@campuscore.com");

        FeeDto.PaymentRequest request = new FeeDto.PaymentRequest();
        request.setInvoiceId(800L);
        request.setPaidAmount(new BigDecimal("50000.00"));
        request.setMode("upi");
        request.setReferenceNo("TXN12345");

        when(userRepository.findByEmail("alice@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(invoiceRepository.findById(800L)).thenReturn(Optional.of(sampleInvoice));

        when(paymentRepository.save(any(FeePayment.class))).thenAnswer(invocation -> {
            FeePayment payment = invocation.getArgument(0);
            payment.setPaymentId(900L);
            return payment;
        });

        FeeDto.PaymentResponse response = feeService.recordPayment(request);

        assertNotNull(response);
        assertEquals(900L, response.getPaymentId());
        assertEquals("RECEIVED", response.getStatus());
        assertEquals(FeeInvoice.InvoiceStatus.PAID, sampleInvoice.getStatus());
        assertEquals(0, sampleInvoice.getNetPayable().compareTo(BigDecimal.ZERO));
        verify(invoiceRepository).save(sampleInvoice);
        verify(eventPublisher).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void recordPayment_PartialPaymentSuccess() {
        mockSecurityContext("alice@campuscore.com");

        FeeDto.PaymentRequest request = new FeeDto.PaymentRequest();
        request.setInvoiceId(800L);
        request.setPaidAmount(new BigDecimal("20000.00"));
        request.setMode("card");

        when(userRepository.findByEmail("alice@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(invoiceRepository.findById(800L)).thenReturn(Optional.of(sampleInvoice));

        feeService.recordPayment(request);

        assertEquals(FeeInvoice.InvoiceStatus.PARTIALLY_PAID, sampleInvoice.getStatus());
        assertEquals(0, sampleInvoice.getNetPayable().compareTo(new BigDecimal("30000.00")));
    }

    @Test
    void recordPayment_ThrowsException_WhenPaymentExceedsNetPayable() {
        mockSecurityContext("alice@campuscore.com");

        FeeDto.PaymentRequest request = new FeeDto.PaymentRequest();
        request.setInvoiceId(800L);
        request.setPaidAmount(new BigDecimal("60000.00"));
        request.setMode("upi");

        when(userRepository.findByEmail("alice@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(invoiceRepository.findById(800L)).thenReturn(Optional.of(sampleInvoice));

        FeeException ex = assertThrows(FeeException.class, () -> feeService.recordPayment(request));
        assertTrue(ex.getMessage().contains("Payment amount exceeds remaining payable dues"));
    }

    // ─────────────────────────────────────────────────────────
    // PROOF OF PAYMENT TEST CASES
    // ─────────────────────────────────────────────────────────

    @Test
    void submitPaymentProof_Success() {
        mockSecurityContext("alice@campuscore.com");

        MockMultipartFile proofFile = new MockMultipartFile("proofFile", "receipt.pdf", "application/pdf", "dummy pdf content".getBytes());

        when(userRepository.findByEmail("alice@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(invoiceRepository.findById(800L)).thenReturn(Optional.of(sampleInvoice));
        when(fileStorageService.store(eq("fee-proofs"), eq("800"), any(MultipartFile.class))).thenReturn("fee-proofs/800/receipt.pdf");

        when(paymentRepository.save(any(FeePayment.class))).thenAnswer(invocation -> {
            FeePayment payment = invocation.getArgument(0);
            payment.setPaymentId(950L);
            return payment;
        });

        FeeDto.PaymentResponse response = feeService.submitPaymentProof(800L, new BigDecimal("50000.00"), "BANK_TRANSFER", "REF98765", proofFile);

        assertNotNull(response);
        assertEquals(950L, response.getPaymentId());
        assertEquals("PENDING_VERIFICATION", response.getStatus());
        assertTrue(response.isHasProof());
        verify(eventPublisher).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void submitPaymentProof_ThrowsException_WhenInvalidMode() {
        mockSecurityContext("alice@campuscore.com");
        MockMultipartFile proofFile = new MockMultipartFile("proofFile", "receipt.jpg", "image/jpeg", "dummy image".getBytes());

        when(userRepository.findByEmail("alice@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(invoiceRepository.findById(800L)).thenReturn(Optional.of(sampleInvoice));

        FeeException ex = assertThrows(FeeException.class, () ->
                feeService.submitPaymentProof(800L, new BigDecimal("50000.00"), "UPI", "REF98765", proofFile));
        assertTrue(ex.getMessage().contains("Proof-of-payment upload is only used for DD or BANK_TRANSFER modes"));
    }

    @Test
    void confirmProofPayment_Success() {
        FeePayment pendingPayment = new FeePayment();
        pendingPayment.setPaymentId(950L);
        pendingPayment.setInvoice(sampleInvoice);
        pendingPayment.setPaidAmount(new BigDecimal("50000.00"));
        pendingPayment.setMode(FeePayment.PaymentMode.BANK_TRANSFER);
        pendingPayment.setStatus(FeePayment.PaymentStatus.PENDING_VERIFICATION);

        when(paymentRepository.findById(950L)).thenReturn(Optional.of(pendingPayment));

        FeeDto.PaymentResponse response = feeService.confirmProofPayment(950L);

        assertNotNull(response);
        assertEquals("RECEIVED", response.getStatus());
        assertEquals(FeeInvoice.InvoiceStatus.PAID, sampleInvoice.getStatus());
        verify(paymentRepository).save(pendingPayment);
        verify(eventPublisher).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void rejectProofPayment_Success() {
        FeePayment pendingPayment = new FeePayment();
        pendingPayment.setPaymentId(950L);
        pendingPayment.setInvoice(sampleInvoice);
        pendingPayment.setPaidAmount(new BigDecimal("50000.00"));
        pendingPayment.setMode(FeePayment.PaymentMode.DD);
        pendingPayment.setStatus(FeePayment.PaymentStatus.PENDING_VERIFICATION);

        when(paymentRepository.findById(950L)).thenReturn(Optional.of(pendingPayment));

        FeeDto.PaymentResponse response = feeService.rejectProofPayment(950L, "Illegible receipt image");

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        assertEquals("Illegible receipt image", response.getVerificationReason());
        verify(paymentRepository).save(pendingPayment);
        verify(eventPublisher).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void downloadProof_Success() {
        mockSecurityContext("alice@campuscore.com");

        FeePayment paymentWithProof = new FeePayment();
        paymentWithProof.setPaymentId(950L);
        paymentWithProof.setInvoice(sampleInvoice);
        paymentWithProof.setProofPath("fee-proofs/800/receipt.pdf");

        when(userRepository.findByEmail("alice@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(paymentRepository.findById(950L)).thenReturn(Optional.of(paymentWithProof));
        when(fileStorageService.read("fee-proofs/800/receipt.pdf")).thenReturn("file content bytes".getBytes());

        byte[] content = feeService.downloadProof(950L);

        assertNotNull(content);
        assertTrue(content.length > 0);
    }

    // ─────────────────────────────────────────────────────────
    // READ & QUERY OPERATION TEST CASES
    // ─────────────────────────────────────────────────────────

    @Test
    void getStudentInvoices_Success() {
        mockSecurityContext("alice@campuscore.com");
        when(userRepository.findByEmail("alice@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(invoiceRepository.findByStudentUserId(700L)).thenReturn(Collections.singletonList(sampleInvoice));

        List<FeeDto.InvoiceResponse> results = feeService.getStudentInvoices(700L);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void getInvoicePayments_Success() {
        mockSecurityContext("alice@campuscore.com");
        when(userRepository.findByEmail("alice@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(invoiceRepository.findById(800L)).thenReturn(Optional.of(sampleInvoice));
        when(paymentRepository.findByInvoice_InvoiceId(800L)).thenReturn(Collections.singletonList(samplePayment));

        List<FeeDto.PaymentResponse> results = feeService.getInvoicePayments(800L);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void getInvoicesByStatus_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<FeeInvoice> pagedInvoices = new PageImpl<>(Collections.singletonList(sampleInvoice));

        when(invoiceRepository.findByStatus(FeeInvoice.InvoiceStatus.GENERATED, pageable)).thenReturn(pagedInvoices);

        Page<FeeDto.InvoiceResponse> resultPage = feeService.getInvoicesByStatus("generated", pageable);

        assertNotNull(resultPage);
        assertEquals(1, resultPage.getContent().size());
        assertEquals("GENERATED", resultPage.getContent().get(0).getStatus());
    }
}