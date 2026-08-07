package com.campuscore.service;

import com.campuscore.dto.ExamDto;
import com.campuscore.dto.GradeDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.*;
import com.campuscore.exception.ExamException;
import com.campuscore.exception.ResourceNotFoundException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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

        sampleCourse = Course.builder()
                .courseId(10L)
                .courseName("Database Systems")
                .courseCode("CS201")
                .semester(1)
                .credits(4)
                .faculty(facultyUser)
                .build();

        sampleExam = ExamSchedule.builder()
                .examId(100L)
                .course(sampleCourse)
                .semester(1)
                .academicYear("2026-2027")
                .examType(ExamSchedule.ExamType.MID)
                .examDate(LocalDate.of(2026, 9, 15)) // Regular weekday
                .startTime(LocalTime.of(8, 30))
                .durationMins(90)
                .venue("Hall A")
                .maxMarks(BigDecimal.valueOf(100))
                .status(ExamSchedule.ExamStatus.SCHEDULED)
                .build();

        sampleInvoice = FeeInvoice.builder()
                .invoiceId(50L)
                .student(studentUser)
                .academicYear("2026-2027")
                .semester(1)
                .status(FeeInvoice.InvoiceStatus.PAID)
                .build();

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
                .academicYear("2026-2027")
                .attendancePercent(BigDecimal.valueOf(75.0))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityUser(User user) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    // ─────────────────────────────────────────────────────────
    // 1. SCHEDULE EXAM TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void scheduleExam_Success() {
        ExamDto.CreateRequest request = new ExamDto.CreateRequest();
        request.setCourseId(10L);
        request.setSemester(1);
        request.setAcademicYear("2026-2027");
        request.setExamType("MID");
        request.setExamDate(LocalDate.of(2026, 9, 15));
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
        request.setSemester(2); // sampleCourse is Semester 1

        when(courseRepository.findById(10L)).thenReturn(Optional.of(sampleCourse));

        ExamException ex = assertThrows(ExamException.class, () -> examService.scheduleExam(request));
        assertTrue(ex.getMessage().contains("is structured for semester 1"));
    }

    @Test
    void scheduleExam_ThrowsException_WhenTimeOverlapsBreak() {
        ExamDto.CreateRequest request = new ExamDto.CreateRequest();
        request.setCourseId(10L);
        request.setSemester(1);
        request.setExamDate(LocalDate.of(2026, 9, 15));
        request.setStartTime(LocalTime.of(11, 30));
        request.setDurationMins(90); // 11:30 - 13:00 overlaps Lunch break (12:00 - 13:00)

        when(courseRepository.findById(10L)).thenReturn(Optional.of(sampleCourse));

        ExamException ex = assertThrows(ExamException.class, () -> examService.scheduleExam(request));
        assertTrue(ex.getMessage().contains("overlaps a scheduled break"));
    }

    // ─────────────────────────────────────────────────────────
    // 2. ENTER GRADES TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void enterGrades_Success() {
        mockSecurityUser(facultyUser);
        sampleExam.setStatus(ExamSchedule.ExamStatus.CONDUCTED); // Required status for grading

        GradeDto.EnterGradeRequest req = new GradeDto.EnterGradeRequest();
        req.setStudentId(2L);
        req.setMarksObtained(BigDecimal.valueOf(85));

        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));
        when(userRepository.findById(1L)).thenReturn(Optional.of(facultyUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(studentUser));
        when(feeInvoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(2L, "2026-2027", 1))
                .thenReturn(Optional.of(sampleInvoice));
        when(registrationRepository.findByStudentUserId(2L)).thenReturn(List.of(sampleRegistration));
        when(attendanceSummaryRepository.findByStudentUserIdAndCourseCourseIdAndSemesterAndAcademicYear(2L, 10L, 1, "2026-2027"))
                .thenReturn(Optional.of(sampleAttendance));
        when(gradeRepository.findByExamExamIdAndStudentUserId(100L, 2L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> examService.enterGrades(100L, List.of(req), 1L));
        verify(gradeRepository, times(1)).save(any(GradeRecord.class));
    }

    @Test
    void enterGrades_ThrowsException_WhenExamNotConducted() {
        mockSecurityUser(facultyUser);
        sampleExam.setStatus(ExamSchedule.ExamStatus.SCHEDULED); // Not CONDUCTED yet

        GradeDto.EnterGradeRequest req = new GradeDto.EnterGradeRequest();
        req.setStudentId(2L);

        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));

        ExamException ex = assertThrows(ExamException.class, () -> examService.enterGrades(100L, List.of(req), 1L));
        assertTrue(ex.getMessage().contains("Grades can only be entered for exams marked as CONDUCTED"));
    }

    @Test
    void enterGrades_ThrowsException_WhenFeesUnpaid() {
        mockSecurityUser(facultyUser);
        sampleExam.setStatus(ExamSchedule.ExamStatus.CONDUCTED);
        sampleInvoice.setStatus(FeeInvoice.InvoiceStatus.GENERATED); // Fees unpaid

        GradeDto.EnterGradeRequest req = new GradeDto.EnterGradeRequest();
        req.setStudentId(2L);

        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));
        when(userRepository.findById(1L)).thenReturn(Optional.of(facultyUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(studentUser));
        when(feeInvoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(2L, "2026-2027", 1))
                .thenReturn(Optional.of(sampleInvoice));

        ExamException ex = assertThrows(ExamException.class, () -> examService.enterGrades(100L, List.of(req), 1L));
        assertTrue(ex.getMessage().contains("Fee Payment Required"));
    }

    @Test
    void enterGrades_ThrowsException_WhenAttendanceBelowLimit() {
        mockSecurityUser(facultyUser);
        sampleExam.setStatus(ExamSchedule.ExamStatus.CONDUCTED);
        sampleAttendance.setAttendancePercent(BigDecimal.valueOf(25.0)); // Mandatory cut-off is 30%

        GradeDto.EnterGradeRequest req = new GradeDto.EnterGradeRequest();
        req.setStudentId(2L);

        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));
        when(userRepository.findById(1L)).thenReturn(Optional.of(facultyUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(studentUser));
        when(feeInvoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(2L, "2026-2027", 1))
                .thenReturn(Optional.of(sampleInvoice));
        when(registrationRepository.findByStudentUserId(2L)).thenReturn(List.of(sampleRegistration));
        when(attendanceSummaryRepository.findByStudentUserIdAndCourseCourseIdAndSemesterAndAcademicYear(2L, 10L, 1, "2026-2027"))
                .thenReturn(Optional.of(sampleAttendance));

        ExamException ex = assertThrows(ExamException.class, () -> examService.enterGrades(100L, List.of(req), 1L));
        assertTrue(ex.getMessage().contains("Eligibility Failed"));
    }

    @Test
    void enterGrades_ThrowsException_WhenMarksExceedMaximum() {
        mockSecurityUser(facultyUser);
        sampleExam.setStatus(ExamSchedule.ExamStatus.CONDUCTED);

        GradeDto.EnterGradeRequest req = new GradeDto.EnterGradeRequest();
        req.setStudentId(2L);
        req.setMarksObtained(BigDecimal.valueOf(105)); // Exceeds maxMarks (100)

        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));
        when(userRepository.findById(1L)).thenReturn(Optional.of(facultyUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(studentUser));
        when(feeInvoiceRepository.findByStudentUserIdAndAcademicYearAndSemester(2L, "2026-2027", 1))
                .thenReturn(Optional.of(sampleInvoice));
        when(registrationRepository.findByStudentUserId(2L)).thenReturn(List.of(sampleRegistration));
        when(attendanceSummaryRepository.findByStudentUserIdAndCourseCourseIdAndSemesterAndAcademicYear(2L, 10L, 1, "2026-2027"))
                .thenReturn(Optional.of(sampleAttendance));

        ExamException ex = assertThrows(ExamException.class, () -> examService.enterGrades(100L, List.of(req), 1L));
        assertTrue(ex.getMessage().contains("exceeds the maximum marks"));
    }

    // ─────────────────────────────────────────────────────────
    // 3. EXAM LIFECYCLE & STATUS TRANSITION TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void markExamConducted_Success() {
        sampleExam.setStatus(ExamSchedule.ExamStatus.SCHEDULED);
        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));
        when(examRepository.save(any(ExamSchedule.class))).thenAnswer(i -> i.getArgument(0));

        ExamDto.Response response = examService.markExamConducted(100L);

        assertNotNull(response);
        assertEquals("CONDUCTED", response.getStatus());
        verify(examRepository).save(sampleExam);
    }

    @Test
    void cancelExam_Success() {
        sampleExam.setStatus(ExamSchedule.ExamStatus.SCHEDULED);
        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));
        when(examRepository.save(any(ExamSchedule.class))).thenAnswer(i -> i.getArgument(0));

        ExamDto.Response response = examService.cancelExam(100L);

        assertNotNull(response);
        assertEquals("CANCELLED", response.getStatus());
        verify(examRepository).save(sampleExam);
    }

    // ─────────────────────────────────────────────────────────
    // 4. PUBLISH GRADES & RESULTS TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void publishGrades_Success() {
        sampleExam.setStatus(ExamSchedule.ExamStatus.CONDUCTED);
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

        assertEquals(GradeRecord.GradeStatus.PUBLISHED, gradeRec.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void compileResultCard_Success() {
        sampleExam.setStatus(ExamSchedule.ExamStatus.CONDUCTED);
        GradeRecord gradeRec = GradeRecord.builder()
                .gradeId(1L)
                .exam(sampleExam)
                .student(studentUser)
                .grade("A")
                .status(GradeRecord.GradeStatus.PUBLISHED)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(studentUser));
        when(gradeRepository.findByStudentUserId(2L)).thenReturn(List.of(gradeRec));
        when(resultRepository.findByStudentUserIdAndAcademicYearAndSemester(2L, "2026-2027", 1))
                .thenReturn(Optional.empty());

        GradeDto.ResultResponse response = examService.compileResultCard(2L, "2026-2027", 1);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(9.00).setScale(2), response.getSgpa());
        assertEquals(0, response.getBacklogs());
        verify(resultRepository, times(1)).save(any(ResultCard.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    // ─────────────────────────────────────────────────────────
    // 5. SECURITY & ACCESS CONTROL TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getExamGrades_ThrowsException_WhenRequestedByStudent() {
        mockSecurityUser(studentUser);
        when(examRepository.findById(100L)).thenReturn(Optional.of(sampleExam));

        ExamException ex = assertThrows(ExamException.class, () -> examService.getExamGrades(100L));
        assertTrue(ex.getMessage().contains("Students are not authorized to access global exam sheets"));
    }

    @Test
    void getStudentGrades_ThrowsException_WhenAccessingOtherStudentData() {
        mockSecurityUser(studentUser);

        ExamException ex = assertThrows(ExamException.class, () -> examService.getStudentGrades(99L));
        assertTrue(ex.getMessage().contains("You cannot request grade sheets belonging to other student accounts"));
    }

    @Test
    void getStudentGrades_Success_WhenAccessingOwnData() {
        mockSecurityUser(studentUser);
        when(gradeRepository.findByStudentUserId(2L)).thenReturn(Collections.emptyList());

        List<GradeDto.Response> result = examService.getStudentGrades(2L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getExamsBySemester_SuccessForFaculty() {
        mockSecurityUser(facultyUser);
        Pageable pageable = PageRequest.of(0, 10);
        Page<ExamSchedule> page = new PageImpl<>(List.of(sampleExam));

        when(examRepository.findByAcademicYearAndSemesterAndCourseFacultyUserId("2026-2027", 1, 1L, pageable))
                .thenReturn(page);

        Page<ExamDto.Response> resultPage = examService.getExamsBySemester("2026-2027", 1, pageable);

        assertNotNull(resultPage);
        assertEquals(1, resultPage.getContent().size());
        assertEquals("CS201", resultPage.getContent().get(0).getCourseCode());
    }
}