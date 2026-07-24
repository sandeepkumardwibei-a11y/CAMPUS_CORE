package com.campuscore.service;

import com.campuscore.dto.FeeDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.FeeInvoice;
import com.campuscore.entity.FeePayment;
import com.campuscore.entity.User;
import com.campuscore.exception.FeeException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.FeeInvoiceRepository;
import com.campuscore.repository.FeePaymentRepository;
import com.campuscore.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeeServiceTest {

    @Mock
    private FeeInvoiceRepository invoiceRepository;

    @Mock
    private FeePaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private FeeService feeService;

    private User studentUser;
    private User accountsUser;
    private FeeInvoice sampleInvoice;
    private FeePayment samplePayment;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        studentUser = User.builder()
                .userId(1L)
                .name("John Doe")
                .email("student@campuscore.com")
                .role(User.Role.STUDENT)
                .build();

        accountsUser = User.builder()
                .userId(2L)
                .name("Accounts Officer")
                .email("accounts@campuscore.com")
                .role(User.Role.ACCOUNTS)
                .build();

        sampleInvoice = FeeInvoice.builder()
                .invoiceId(10L)
                .student(studentUser)
                .academicYear("2025-2026")
                .semester(1)
                .tuitionFee(BigDecimal.valueOf(50000))
                .libraryFee(BigDecimal.valueOf(2000))
                .labFee(BigDecimal.valueOf(3000))
                .activityFee(BigDecimal.valueOf(1000))
                .totalAmount(BigDecimal.valueOf(56000))
                .scholarshipAdjusted(BigDecimal.valueOf(6000))
                .netPayable(BigDecimal.valueOf(50000))
                .dueDate(LocalDate.now().plusDays(30))
                .status(FeeInvoice.InvoiceStatus.GENERATED)
                .build();

        samplePayment = FeePayment.builder()
                .paymentId(100L)
                .invoice(sampleInvoice)
                .paidAmount(BigDecimal.valueOf(20000))
                .paymentDate(LocalDate.now())
                .mode(FeePayment.PaymentMode.BANK_TRANSFER)
                .referenceNo("REF123456")
                .receiptNumber("RCPT-12345678")
                .status(FeePayment.PaymentStatus.RECEIVED)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityUser(User user) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(user.getEmail());
        when(authentication.getName()).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    // ─────────────────────────────────────────────────────────
    // 1. GENERATE INVOICE TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void generateInvoice_Success() {
        FeeDto.InvoiceCreateRequest req = new FeeDto.InvoiceCreateRequest();
        req.setStudentId(1L);
        req.setAcademicYear("2025-2026");
        req.setSemester(1);
        req.setTuitionFee(BigDecimal.valueOf(50000));
        req.setLibraryFee(BigDecimal.valueOf(2000));
        req.setLabFee(BigDecimal.valueOf(3000));
        req.setActivityFee(BigDecimal.valueOf(1000));
        req.setScholarshipAdjusted(BigDecimal.valueOf(6000));
        req.setDueDate(LocalDate.now().plusDays(30));

        when(userRepository.findById(1L)).thenReturn(Optional.of(studentUser));
        when(invoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(1L, "2025-2026", 1))
                .thenReturn(Optional.empty());
        when(invoiceRepository.save(any(FeeInvoice.class))).thenReturn(sampleInvoice);

        FeeDto.InvoiceResponse response = feeService.generateInvoice(req);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(50000), response.getNetPayable());
        verify(invoiceRepository, times(1)).save(any(FeeInvoice.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void generateInvoice_ThrowsException_WhenDuplicateInvoice() {
        FeeDto.InvoiceCreateRequest req = new FeeDto.InvoiceCreateRequest();
        req.setStudentId(1L);
        req.setAcademicYear("2025-2026");
        req.setSemester(1);

        when(userRepository.findById(1L)).thenReturn(Optional.of(studentUser));
        when(invoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(1L, "2025-2026", 1))
                .thenReturn(Optional.of(sampleInvoice));

        assertThrows(FeeException.class, () -> feeService.generateInvoice(req));
    }

    // ─────────────────────────────────────────────────────────
    // 2. RECORD PAYMENT TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void recordPayment_Success() {
        mockSecurityUser(studentUser);

        FeeDto.PaymentRequest req = new FeeDto.PaymentRequest();
        req.setInvoiceId(10L);
        req.setPaidAmount(BigDecimal.valueOf(20000));
        req.setMode("ONLINE");
        req.setReferenceNo("TXN12345");

        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(sampleInvoice));
        when(paymentRepository.save(any(FeePayment.class))).thenReturn(samplePayment);

        FeeDto.PaymentResponse response = feeService.recordPayment(req);

        assertNotNull(response);
        verify(paymentRepository, times(1)).save(any(FeePayment.class));
        verify(invoiceRepository, times(1)).save(sampleInvoice);
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void recordPayment_ThrowsException_WhenAmountExceedsDues() {
        mockSecurityUser(studentUser);

        FeeDto.PaymentRequest req = new FeeDto.PaymentRequest();
        req.setInvoiceId(10L);
        req.setPaidAmount(BigDecimal.valueOf(60000)); // Exceeds 50000 net payable
        req.setMode("ONLINE");

        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(sampleInvoice));

        assertThrows(FeeException.class, () -> feeService.recordPayment(req));
    }

    // ─────────────────────────────────────────────────────────
    // 3. PROOF OF PAYMENT TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void submitPaymentProof_Success() {
        mockSecurityUser(studentUser);
        MultipartFile mockFile = new MockMultipartFile("file", "proof.pdf", "application/pdf", new byte[]{1, 2, 3});

        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(sampleInvoice));
        when(fileStorageService.store(eq("fee-proofs"), eq("10"), any())).thenReturn("fee-proofs/10/proof.pdf");
        when(paymentRepository.save(any(FeePayment.class))).thenReturn(samplePayment);

        FeeDto.PaymentResponse response = feeService.submitPaymentProof(
                10L, BigDecimal.valueOf(20000), "BANK_TRANSFER", "REF999", mockFile
        );

        assertNotNull(response);
        verify(fileStorageService, times(1)).store(eq("fee-proofs"), eq("10"), any());
        verify(paymentRepository, times(1)).save(any(FeePayment.class));
    }

    @Test
    void confirmProofPayment_Success() {
        samplePayment.setStatus(FeePayment.PaymentStatus.PENDING_VERIFICATION);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(samplePayment));

        FeeDto.PaymentResponse response = feeService.confirmProofPayment(100L);

        assertNotNull(response);
        assertEquals("RECEIVED", response.getStatus());
        verify(paymentRepository, times(1)).save(samplePayment);
        verify(invoiceRepository, times(1)).save(sampleInvoice);
    }

    @Test
    void rejectProofPayment_Success() {
        samplePayment.setStatus(FeePayment.PaymentStatus.PENDING_VERIFICATION);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(samplePayment));

        FeeDto.PaymentResponse response = feeService.rejectProofPayment(100L, "Illegible receipt");

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        verify(paymentRepository, times(1)).save(samplePayment);
    }

    // ─────────────────────────────────────────────────────────
    // 4. FILE DOWNLOAD & QUERY TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void downloadProof_Success() {
        mockSecurityUser(studentUser);
        samplePayment.setProofPath("fee-proofs/10/proof.pdf");

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(samplePayment));
        when(fileStorageService.read("fee-proofs/10/proof.pdf")).thenReturn(new byte[]{1, 2, 3});

        byte[] result = feeService.downloadProof(100L);

        assertNotNull(result);
        assertEquals(3, result.length);
    }

    @Test
    void getStudentInvoices_Success() {
        mockSecurityUser(studentUser);

        when(invoiceRepository.findByStudentUserId(1L)).thenReturn(List.of(sampleInvoice));

        List<FeeDto.InvoiceResponse> result = feeService.getStudentInvoices(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getInvoicesByStatus_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<FeeInvoice> page = new PageImpl<>(List.of(sampleInvoice));

        when(invoiceRepository.findByStatus(FeeInvoice.InvoiceStatus.GENERATED, pageable)).thenReturn(page);

        Page<FeeDto.InvoiceResponse> result = feeService.getInvoicesByStatus("GENERATED", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }
}