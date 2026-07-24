package com.campuscore.service;

import com.campuscore.dto.ExamDto;
import com.campuscore.dto.GradeDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.*;
import com.campuscore.exception.ExamException;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamServiceTest {

    @Mock
    private ExamScheduleRepository examRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GradeRecordRepository gradeRepository;

    @Mock
    private ResultCardRepository resultRepository;

    @Mock
    private SemesterRegistrationRepository registrationRepository;

    @Mock
    private AttendanceSummaryRepository attendanceSummaryRepository;

    @Mock
    private FeeInvoiceRepository feeInvoiceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ExamService examService;

    private Course sampleCourse;
    private User facultyUser;
    private User studentUser;
    private ExamSchedule sampleExam;
    private FeeInvoice sampleInvoice;
    private SemesterRegistration sampleRegistration;
    private AttendanceSummary sampleAttendance;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        sampleCourse = Course.builder()
                .courseId(10L)
                .courseName("Database Systems")
                .courseCode("CS201")
                .semester(1)
                .credits(4)
                .build();

        facultyUser = User.builder()
                .userId(1L)
                .name("Dr. Smith")
                .email("faculty@campuscore.com")
                .role(User.Role.FACULTY)
                .build();

        studentUser = User.builder()
                .userId(2L)
                .name("Jane Doe")
                .email("student@campuscore.com")
                .role(User.Role.STUDENT)
                .build();

        sampleExam = ExamSchedule.builder()
                .examId(100L)
                .course(sampleCourse)
                .semester(1)
                .academicYear("2025-2026")
                .examType(ExamSchedule.ExamType.MID)
                .examDate(LocalDate.of(2026, 8, 10))
                .startTime(LocalTime.of(8, 30))
                .durationMins(90)
                .venue("Hall A")
                .maxMarks(BigDecimal.valueOf(100))
                .status(ExamSchedule.ExamStatus.SCHEDULED)
                .build();

        sampleInvoice = FeeInvoice.builder()
                .invoiceId(50L)
                .student(studentUser)
                .academicYear("2025-2026")
                .semester(1)
                .status(FeeInvoice.InvoiceStatus.PAID)
                .build();

        // 🎯 FIXED: Changed List.of(sampleCourse) -> Set.of(sampleCourse) for Set<Course> mapping
        sampleRegistration = SemesterRegistration.builder()
                .registrationId(30L)
                .student(studentUser)
                .courses(Set.of(sampleCourse))
                .build();

        sampleAttendance = AttendanceSummary.builder()
                .summaryId(40L)
                .student(studentUser)
                .course(sampleCourse)
                .semester(1)
                .academicYear("2025-2026")
                .attendancePercent(BigDecimal.valueOf(75.0))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────
    // 1. SCHEDULE EXAM TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void scheduleExam_Success() {
        ExamDto.CreateRequest request = new ExamDto.CreateRequest();
        request.setCourseId(10L);
        request.setSemester(1);
        request.setAcademicYear("2025-2026");
        request.setExamType("MID");
        request.setExamDate(LocalDate.of(2026, 8, 10));
        request.setStartTime(LocalTime.of(8, 30));
        request.setDurationMins(90);
        request.setVenue("Hall A");
        request.setMaxMarks(BigDecimal.valueOf(100));

        when(courseRepository.findById(10L)).thenReturn(Optional.of(sampleCourse));
        when(examRepository.save(any(ExamSchedule.class))).thenReturn(sampleExam);

        ExamDto.Response response = examService.scheduleExam(request);

        assertNotNull(response);
        assertEquals("CS201", response.getCourseCode());
        assertEquals("MID", response.getExamType());
        verify(examRepository, times(1)).save(any(ExamSchedule.class));
    }

    @Test
    void scheduleExam_ThrowsException_WhenCourseSemesterMismatches() {
        ExamDto.CreateRequest request = new ExamDto.CreateRequest();
        request.setCourseId(10L);
        request.setSemester(2);

        when(courseRepository.findById(10L)).thenReturn(Optional.of(sampleCourse));

        assertThrows(ExamException.class, () -> examService.scheduleExam(request));
    }

    @Test
    void scheduleExam_ThrowsException_WhenTimeOverlapsBreak() {
        ExamDto.CreateRequest request = new ExamDto.CreateRequest();
        request.setCourseId(10L);
        request.setSemester(1);
        request.setExamDate(LocalDate.of(2026, 8, 10));
        request.setStartTime(LocalTime.of(11, 30));
        request.setDurationMins(90);

        when(courseRepository.findById(10L)).thenReturn(Optional.of(sampleCourse));

        assertThrows(ExamException.class, () -> examService.scheduleExam(request));
    }

    // ─────────────────────────────────────────────────────────
    // 2. ENTER GRADES TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void enterGrades_Success() {
        GradeDto.EnterGradeRequest req = new GradeDto.EnterGradeRequest();
        req.setStudentId(2L);
        req.setMarksObtained(BigDecimal.valueOf(85));

        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));
        when(userRepository.findById(1L)).thenReturn(Optional.of(facultyUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(studentUser));
        when(feeInvoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(2L, "2025-2026", 1))
                .thenReturn(Optional.of(sampleInvoice));
        when(registrationRepository.findByStudentUserId(2L)).thenReturn(List.of(sampleRegistration));
        when(attendanceSummaryRepository.findByStudentUserIdAndCourseCourseIdAndSemesterAndAcademicYear(2L, 10L, 1, "2025-2026"))
                .thenReturn(Optional.of(sampleAttendance));
        when(gradeRepository.findByExamExamIdAndStudentUserId(100L, 2L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> examService.enterGrades(100L, List.of(req), 1L));
        verify(gradeRepository, times(1)).save(any(GradeRecord.class));
    }

    @Test
    void enterGrades_ThrowsException_WhenFeesUnpaid() {
        // 🎯 FIXED: Dynamically picks a non-PAID/non-WAIVED status enum constant from FeeInvoice.InvoiceStatus
        FeeInvoice.InvoiceStatus unpaidStatus = Arrays.stream(FeeInvoice.InvoiceStatus.values())
                .filter(s -> s != FeeInvoice.InvoiceStatus.PAID && s != FeeInvoice.InvoiceStatus.WAIVED)
                .findFirst()
                .orElse(null);

        sampleInvoice.setStatus(unpaidStatus);
        GradeDto.EnterGradeRequest req = new GradeDto.EnterGradeRequest();
        req.setStudentId(2L);

        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));
        when(userRepository.findById(1L)).thenReturn(Optional.of(facultyUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(studentUser));
        when(feeInvoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(2L, "2025-2026", 1))
                .thenReturn(Optional.of(sampleInvoice));

        assertThrows(ExamException.class, () -> examService.enterGrades(100L, List.of(req), 1L));
    }

    @Test
    void enterGrades_ThrowsException_WhenAttendanceBelowLimit() {
        sampleAttendance.setAttendancePercent(BigDecimal.valueOf(25.0));
        GradeDto.EnterGradeRequest req = new GradeDto.EnterGradeRequest();
        req.setStudentId(2L);

        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));
        when(userRepository.findById(1L)).thenReturn(Optional.of(facultyUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(studentUser));
        when(feeInvoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(2L, "2025-2026", 1))
                .thenReturn(Optional.of(sampleInvoice));
        when(registrationRepository.findByStudentUserId(2L)).thenReturn(List.of(sampleRegistration));
        when(attendanceSummaryRepository.findByStudentUserIdAndCourseCourseIdAndSemesterAndAcademicYear(2L, 10L, 1, "2025-2026"))
                .thenReturn(Optional.of(sampleAttendance));

        assertThrows(ExamException.class, () -> examService.enterGrades(100L, List.of(req), 1L));
    }

    // ─────────────────────────────────────────────────────────
    // 3. PUBLISH GRADES & RESULTS TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void publishGrades_Success() {
        GradeRecord gradeRec = GradeRecord.builder()
                .gradeId(1L)
                .exam(sampleExam)
                .student(studentUser)
                .grade("A")
                .status(GradeRecord.GradeStatus.DRAFT)
                .build();

        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));
        when(gradeRepository.findByExamExamId(100L)).thenReturn(List.of(gradeRec));

        examService.publishGrades(100L);

        assertEquals(ExamSchedule.ExamStatus.CONDUCTED, sampleExam.getStatus());
        assertEquals(GradeRecord.GradeStatus.PUBLISHED, gradeRec.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void compileResultCard_Success() {
        GradeRecord gradeRec = GradeRecord.builder()
                .gradeId(1L)
                .exam(sampleExam)
                .student(studentUser)
                .grade("A")
                .status(GradeRecord.GradeStatus.PUBLISHED)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(studentUser));
        when(gradeRepository.findByStudentUserId(2L)).thenReturn(List.of(gradeRec));
        when(resultRepository.findByStudentUserIdAndAcademicYearAndSemester(2L, "2025-2026", 1))
                .thenReturn(Optional.empty());

        GradeDto.ResultResponse response = examService.compileResultCard(2L, "2025-2026", 1);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(9.00).setScale(2), response.getSgpa());
        assertEquals(0, response.getBacklogs());
        verify(resultRepository, times(1)).save(any(ResultCard.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    // ─────────────────────────────────────────────────────────
    // 4. SECURITY & ACCESS CONTROL TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getExamGrades_ThrowsException_WhenRequestedByStudent() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("student@campuscore.com");
        when(userRepository.findByEmail("student@campuscore.com")).thenReturn(Optional.of(studentUser));
        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));

        assertThrows(ExamException.class, () -> examService.getExamGrades(100L));
    }

    @Test
    void getStudentGrades_ThrowsException_WhenAccessingOtherStudentData() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("student@campuscore.com");
        when(userRepository.findByEmail("student@campuscore.com")).thenReturn(Optional.of(studentUser));

        assertThrows(ExamException.class, () -> examService.getStudentGrades(99L));
    }

    @Test
    void getStudentGrades_Success_WhenAccessingOwnData() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("student@campuscore.com");
        when(userRepository.findByEmail("student@campuscore.com")).thenReturn(Optional.of(studentUser));
        when(gradeRepository.findByStudentUserId(2L)).thenReturn(Collections.emptyList());

        List<GradeDto.Response> result = examService.getStudentGrades(2L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}