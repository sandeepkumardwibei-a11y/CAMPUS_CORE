package com.campuscore.service;

import com.campuscore.dto.AdmissionDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.*;
import com.campuscore.exception.AdmissionException;
import com.campuscore.repository.*;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 🎯 Resolves UnnecessaryStubbingException
class AdmissionServiceTest {

    @Mock
    private AdmissionApplicationRepository admissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private OfferLetterRepository offerLetterRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AdmissionService admissionService;

    private User sampleUser;
    private Program sampleProgram;
    private Department sampleDepartment;
    private AdmissionApplication sampleApplication;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        sampleUser = new User();
        sampleUser.setUserId(1L);
        sampleUser.setName("John Doe");
        sampleUser.setEmail("john.doe@example.com");
        sampleUser.setStatus(User.UserStatus.ACTIVE);
        sampleUser.setRole(User.Role.APPLICANT);

        sampleDepartment = new Department();
        sampleDepartment.setDepartmentId(10L);
        sampleDepartment.setDepartmentName("Computer Science");

        sampleProgram = new Program();
        sampleProgram.setProgramId(100L);
        sampleProgram.setProgramName("B.Tech CSE");
        sampleProgram.setDepartmentId(10L);
        sampleProgram.setMinimumPercentage(75.0);

        sampleApplication = AdmissionApplication.builder()
                .applicationId(500L)
                .applicantName("John Doe")
                .email("john.doe@example.com")
                .phone("1234567890")
                .programId(100L)
                .programName("B.Tech CSE")
                .departmentId(10L)
                .departmentName("Computer Science")
                .academicYear("2026-2027")
                .qualifyingScore(85.0)
                .status(AdmissionApplication.ApplicationStatus.SUBMITTED)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthentication(String email, String role) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
        if (role != null) {
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
            doReturn(authorities).when(authentication).getAuthorities();
        }
    }

    // ─────────────────────────────────────────────────────────
    // 1. STAGE 1: SUBMIT APPLICATION TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void submitApplication_Success() {
        mockAuthentication("john.doe@example.com", "ROLE_APPLICANT");

        AdmissionDto.ApplicationRequest request = new AdmissionDto.ApplicationRequest();
        request.setApplicantName("John Doe");
        request.setEmail("john.doe@example.com");
        request.setPhone("1234567890");
        request.setProgramName("B.Tech CSE");
        request.setDepartmentName("Computer Science");
        request.setAcademicYear("2026-2027");
        request.setPercentageSecured(85.0);

        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(sampleUser));
        when(admissionRepository.findAll()).thenReturn(Collections.emptyList());
        when(programRepository.findByProgramNameIgnoreCase("B.Tech CSE")).thenReturn(Optional.of(sampleProgram));
        when(departmentRepository.findByDepartmentNameIgnoreCase("Computer Science")).thenReturn(Optional.of(sampleDepartment));
        when(admissionRepository.saveAndFlush(any(AdmissionApplication.class))).thenReturn(sampleApplication);

        AdmissionApplication result = admissionService.submitApplication(request);

        assertNotNull(result);
        assertEquals("John Doe", result.getApplicantName());
        assertEquals(AdmissionApplication.ApplicationStatus.SUBMITTED, result.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void submitApplication_ThrowsException_WhenUserInactive() {
        mockAuthentication("john.doe@example.com", "ROLE_APPLICANT");
        sampleUser.setStatus(User.UserStatus.INACTIVE);

        AdmissionDto.ApplicationRequest request = new AdmissionDto.ApplicationRequest();
        request.setEmail("john.doe@example.com");

        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(sampleUser));

        assertThrows(AdmissionException.class, () -> admissionService.submitApplication(request));
    }

    @Test
    void submitApplication_ThrowsException_WhenEmailMismatched() {
        mockAuthentication("john.doe@example.com", "ROLE_APPLICANT");

        AdmissionDto.ApplicationRequest request = new AdmissionDto.ApplicationRequest();
        request.setEmail("other@example.com");

        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(sampleUser));

        assertThrows(AdmissionException.class, () -> admissionService.submitApplication(request));
    }

    // ─────────────────────────────────────────────────────────
    // 2. STAGE 2: EVALUATE APPLICATION TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void evaluateApplication_Success_Shortlisted() {
        when(admissionRepository.findById(500L)).thenReturn(Optional.of(sampleApplication));
        when(programRepository.findById(100L)).thenReturn(Optional.of(sampleProgram));
        when(admissionRepository.saveAndFlush(any(AdmissionApplication.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(sampleUser));

        AdmissionApplication result = admissionService.evaluateApplication(500L, true);

        assertEquals(AdmissionApplication.ApplicationStatus.SHORTLISTED, result.getStatus());
        assertNull(result.getRejectionReason());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void evaluateApplication_ThrowsException_WhenScoreBelowCutoff() {
        sampleApplication.setQualifyingScore(60.0); // Minimum cut-off is 75.0

        when(admissionRepository.findById(500L)).thenReturn(Optional.of(sampleApplication));
        when(programRepository.findById(100L)).thenReturn(Optional.of(sampleProgram));

        assertThrows(AdmissionException.class, () -> admissionService.evaluateApplication(500L, true));
    }

    // ─────────────────────────────────────────────────────────
    // 3. STAGE 3: ISSUE OFFER LETTER TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void issueOfferLetter_Success() {
        sampleApplication.setStatus(AdmissionApplication.ApplicationStatus.SHORTLISTED);

        OfferLetter offer = OfferLetter.builder().offerId(1000L).application(sampleApplication).build();

        when(admissionRepository.findById(500L)).thenReturn(Optional.of(sampleApplication));
        when(programRepository.findById(100L)).thenReturn(Optional.of(sampleProgram));
        when(offerLetterRepository.saveAndFlush(any(OfferLetter.class))).thenReturn(offer);
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(sampleUser));

        OfferLetter result = admissionService.issueOfferLetter(500L, "FEE_REF_123");

        assertNotNull(result);
        assertEquals(AdmissionApplication.ApplicationStatus.OFFER_ISSUED, sampleApplication.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    // ─────────────────────────────────────────────────────────
    // 4. STAGE 4A: ACCEPT OFFER TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void acceptOffer_Success() {
        mockAuthentication("john.doe@example.com", "ROLE_APPLICANT");
        sampleApplication.setStatus(AdmissionApplication.ApplicationStatus.OFFER_ISSUED);

        OfferLetter offer = OfferLetter.builder()
                .offerId(1000L)
                .application(sampleApplication)
                .joiningDeadline(LocalDate.now().plusDays(10))
                .build();

        when(admissionRepository.findById(500L)).thenReturn(Optional.of(sampleApplication));
        when(offerLetterRepository.findByApplication(sampleApplication)).thenReturn(Optional.of(offer));
        when(admissionRepository.saveAndFlush(any(AdmissionApplication.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(sampleUser));

        AdmissionApplication result = admissionService.acceptOffer(500L, "Father Name", "Mother Name", "ID123", "123 Street");

        assertEquals(AdmissionApplication.ApplicationStatus.OFFER_ACCEPTED, result.getStatus());
        assertEquals(OfferLetter.OfferStatus.ACCEPTED, offer.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    // ─────────────────────────────────────────────────────────
    // 5. STAGE 6: DOCUMENT VERIFICATION TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void processDocumentVerification_Success_WhenAllUploaded() {
        sampleApplication.setStatus(AdmissionApplication.ApplicationStatus.OFFER_ACCEPTED);
        sampleApplication.setTenthMarksheetPath("path/10th.pdf");
        sampleApplication.setTwelfthMarksheetPath("path/12th.pdf");
        sampleApplication.setAadharCardPath("path/aadhar.pdf");

        when(admissionRepository.findById(500L)).thenReturn(Optional.of(sampleApplication));
        when(admissionRepository.saveAndFlush(any(AdmissionApplication.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(sampleUser));

        AdmissionApplication result = admissionService.processDocumentVerification(500L, true);

        assertEquals(AdmissionApplication.ApplicationStatus.DOCUMENTS_VERIFIED, result.getStatus());
        assertTrue(result.isDocumentsVerified());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void processDocumentVerification_ThrowsException_WhenDocumentsMissing() {
        sampleApplication.setStatus(AdmissionApplication.ApplicationStatus.OFFER_ACCEPTED);

        when(admissionRepository.findById(500L)).thenReturn(Optional.of(sampleApplication));

        assertThrows(AdmissionException.class, () -> admissionService.processDocumentVerification(500L, true));
    }

    // ─────────────────────────────────────────────────────────
    // 6. STAGE 8: FINALIZE ENROLLMENT TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void finalizeEnrollment_Success() {
        mockAuthentication("john.doe@example.com", "ROLE_STUDENT");
        sampleApplication.setStatus(AdmissionApplication.ApplicationStatus.ADMISSION_LETTER_ISSUED);

        when(admissionRepository.findById(500L)).thenReturn(Optional.of(sampleApplication));
        when(admissionRepository.saveAndFlush(any(AdmissionApplication.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(sampleUser));

        AdmissionApplication result = admissionService.finalizeEnrollment(500L);

        assertEquals(AdmissionApplication.ApplicationStatus.ENROLLED, result.getStatus());
        assertEquals(User.Role.STUDENT, sampleUser.getRole());
        assertEquals(10L, sampleUser.getDepartmentId());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    // ─────────────────────────────────────────────────────────
    // 7. DOCUMENT MANAGEMENT TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void uploadDocument_Success() {
        mockAuthentication("john.doe@example.com", "ROLE_APPLICANT");
        MockMultipartFile mockFile = new MockMultipartFile("file", "10th.pdf", "application/pdf", "content".getBytes());

        when(admissionRepository.findById(500L)).thenReturn(Optional.of(sampleApplication));
        when(fileStorageService.store("admissions", "500", mockFile)).thenReturn("admissions/500/10th.pdf");
        when(admissionRepository.saveAndFlush(any(AdmissionApplication.class))).thenAnswer(i -> i.getArgument(0));

        AdmissionApplication result = admissionService.uploadDocument(500L, "TENTH", mockFile);

        assertEquals("admissions/500/10th.pdf", result.getTenthMarksheetPath());
        assertFalse(result.isDocumentsVerified());
    }

    @Test
    void downloadDocument_Success() {
        mockAuthentication("john.doe@example.com", "ROLE_APPLICANT");
        sampleApplication.setTenthMarksheetPath("admissions/500/10th.pdf");

        when(admissionRepository.findById(500L)).thenReturn(Optional.of(sampleApplication));
        when(fileStorageService.read("admissions/500/10th.pdf")).thenReturn("pdf-bytes".getBytes());

        byte[] bytes = admissionService.downloadDocument(500L, "TENTH");

        assertNotNull(bytes);
        assertEquals("pdf-bytes", new String(bytes));
    }
}