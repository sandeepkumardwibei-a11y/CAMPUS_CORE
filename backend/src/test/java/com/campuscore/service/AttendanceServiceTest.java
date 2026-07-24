package com.campuscore.service;

import com.campuscore.dto.AttendanceDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.*;
import com.campuscore.exception.AttendanceException;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceTest {

    @Mock
    private AttendanceRecordRepository recordRepository;

    @Mock
    private AttendanceSummaryRepository summaryRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SemesterRegistrationRepository registrationRepository;

    @Mock
    private FacultyAttendanceRepository facultyAttendanceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AttendanceService attendanceService;

    private User facultyUser;
    private User studentUser;
    private Course sampleCourse;
    private SemesterRegistration sampleRegistration;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        facultyUser = new User();
        facultyUser.setUserId(10L);
        facultyUser.setName("Prof. Alan");
        facultyUser.setEmail("alan@campuscore.com");
        facultyUser.setRole(User.Role.FACULTY);

        studentUser = new User();
        studentUser.setUserId(101L);
        studentUser.setName("Alice Smith");
        studentUser.setEmail("alice@campuscore.com");
        studentUser.setRole(User.Role.STUDENT);

        sampleCourse = new Course();
        sampleCourse.setCourseId(200L);
        sampleCourse.setCourseName("Data Structures");
        sampleCourse.setFaculty(facultyUser);

        sampleRegistration = new SemesterRegistration();
        sampleRegistration.setStudent(studentUser);
        sampleRegistration.setSemester(1);
        sampleRegistration.setAcademicYear("2026-2027");
        sampleRegistration.setCourses(Set.of(sampleCourse)); // 🎯 FIXED: Changed List.of to Set.of
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthenticatedUser(User user) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    // ─────────────────────────────────────────────────────────
    // 1. MARK STUDENT ATTENDANCE TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void markAttendance_Success() {
        mockAuthenticatedUser(facultyUser);

        AttendanceDto.StudentAttendance studentRecord = new AttendanceDto.StudentAttendance();
        studentRecord.setStudentId(101L);
        studentRecord.setStatus("PRESENT");

        AttendanceDto.MarkRequest request = new AttendanceDto.MarkRequest();
        request.setCourseId(200L);
        request.setLectureDate(LocalDate.now());
        request.setRecords(List.of(studentRecord));

        when(courseRepository.findById(200L)).thenReturn(Optional.of(sampleCourse));
        when(userRepository.findById(101L)).thenReturn(Optional.of(studentUser));
        when(recordRepository.findByStudentUserIdAndCourseCourseIdAndLectureDate(101L, 200L, request.getLectureDate()))
                .thenReturn(Optional.empty());

        AttendanceRecord savedRecord = AttendanceRecord.builder()
                .student(studentUser)
                .course(sampleCourse)
                .lectureDate(request.getLectureDate())
                .status(AttendanceRecord.AttendanceStatus.PRESENT)
                .build();

        when(recordRepository.findByStudentUserIdAndCourseCourseId(101L, 200L)).thenReturn(List.of(savedRecord));
        when(registrationRepository.findByStudentUserId(101L)).thenReturn(List.of(sampleRegistration));
        when(courseRepository.getReferenceById(200L)).thenReturn(sampleCourse);

        assertDoesNotThrow(() -> attendanceService.markAttendance(request));

        verify(recordRepository, times(1)).save(any(AttendanceRecord.class));
        verify(summaryRepository, times(1)).save(any(AttendanceSummary.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void markAttendance_ThrowsException_WhenUnauthorizedUser() {
        User otherFaculty = new User();
        otherFaculty.setUserId(99L);
        otherFaculty.setEmail("other@campuscore.com");
        otherFaculty.setRole(User.Role.FACULTY);

        mockAuthenticatedUser(otherFaculty);

        AttendanceDto.MarkRequest request = new AttendanceDto.MarkRequest();
        request.setCourseId(200L);
        request.setRecords(List.of(new AttendanceDto.StudentAttendance()));

        when(courseRepository.findById(200L)).thenReturn(Optional.of(sampleCourse));

        assertThrows(AttendanceException.class, () -> attendanceService.markAttendance(request));
    }

    @Test
    void markAttendance_ThrowsException_WhenTargetIsFaculty() {
        mockAuthenticatedUser(facultyUser);

        AttendanceDto.StudentAttendance studentRecord = new AttendanceDto.StudentAttendance();
        studentRecord.setStudentId(10L); // Assigning Faculty ID
        studentRecord.setStatus("PRESENT");

        AttendanceDto.MarkRequest request = new AttendanceDto.MarkRequest();
        request.setCourseId(200L);
        request.setLectureDate(LocalDate.now());
        request.setRecords(List.of(studentRecord));

        when(courseRepository.findById(200L)).thenReturn(Optional.of(sampleCourse));
        when(userRepository.findById(10L)).thenReturn(Optional.of(facultyUser));

        assertThrows(AttendanceException.class, () -> attendanceService.markAttendance(request));
    }

    // ─────────────────────────────────────────────────────────
    // 2. MARK FACULTY ATTENDANCE TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void markFacultyAttendance_Success() {
        AttendanceDto.FacultyMarkRequest request = new AttendanceDto.FacultyMarkRequest();
        request.setFacultyName("Prof. Alan");
        request.setDate(LocalDate.now());
        request.setStatus("PRESENT");

        when(userRepository.findAll()).thenReturn(List.of(facultyUser));

        AttendanceDto.FacultyResponse response = attendanceService.markFacultyAttendance(request);

        assertNotNull(response);
        assertEquals("Prof. Alan", response.getFacultyName());
        assertEquals("PRESENT", response.getStatus());
        verify(facultyAttendanceRepository, times(1)).save(any(FacultyAttendanceRecord.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void markFacultyAttendance_ThrowsException_WhenFacultyNotFound() {
        AttendanceDto.FacultyMarkRequest request = new AttendanceDto.FacultyMarkRequest();
        request.setFacultyName("Unknown Faculty");
        request.setDate(LocalDate.now());
        request.setStatus("PRESENT");

        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        assertThrows(AttendanceException.class, () -> attendanceService.markFacultyAttendance(request));
    }

    // ─────────────────────────────────────────────────────────
    // 3. GET STUDENT SUMMARIES TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getStudentSummaries_Success() {
        mockAuthenticatedUser(studentUser);

        AttendanceSummary summary = AttendanceSummary.builder()
                .summaryId(50L)
                .student(studentUser)
                .course(sampleCourse)
                .semester(1)
                .academicYear("2026-2027")
                .totalLectures(10)
                .attendedLectures(8)
                .attendancePercent(BigDecimal.valueOf(80.0))
                .shortageFlag(false)
                .build();

        when(userRepository.findById(101L)).thenReturn(Optional.of(studentUser));
        when(summaryRepository.findByStudentUserIdAndAcademicYear(101L, "2026-2027"))
                .thenReturn(List.of(summary));

        List<AttendanceDto.SummaryResponse> result = attendanceService.getStudentSummaries(101L, "2026-2027");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(80.0), result.get(0).getAttendancePercent());
    }

    @Test
    void getStudentSummaries_ThrowsException_WhenStudentAccessesOtherStudentData() {
        User otherStudent = new User();
        otherStudent.setUserId(102L);
        otherStudent.setEmail("bob@campuscore.com");
        otherStudent.setRole(User.Role.STUDENT);

        mockAuthenticatedUser(otherStudent);

        assertThrows(AttendanceException.class, () -> attendanceService.getStudentSummaries(101L, "2026-2027"));
    }

    // ─────────────────────────────────────────────────────────
    // 4. GET FACULTY ATTENDANCE TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getFacultyAttendance_Success() {
        mockAuthenticatedUser(facultyUser);

        FacultyAttendanceRecord record = FacultyAttendanceRecord.builder()
                .id(1L)
                .faculty(facultyUser)
                .date(LocalDate.now())
                .status("PRESENT")
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(facultyUser));
        when(facultyAttendanceRepository.findByFacultyUserId(10L)).thenReturn(List.of(record));

        List<AttendanceDto.FacultyResponse> result = attendanceService.getFacultyAttendance(10L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("PRESENT", result.get(0).getStatus());
    }

    // ─────────────────────────────────────────────────────────
    // 5. SHORTAGE LIST TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getShortageListByCourse_Success() {
        AttendanceSummary shortageSummary = AttendanceSummary.builder()
                .summaryId(51L)
                .student(studentUser)
                .course(sampleCourse)
                .semester(1)
                .academicYear("2026-2027")
                .totalLectures(10)
                .attendedLectures(5)
                .attendancePercent(BigDecimal.valueOf(50.0))
                .shortageFlag(true)
                .build();

        when(courseRepository.existsById(200L)).thenReturn(true);
        when(summaryRepository.findByCourseCourseIdAndShortageFlagTrue(200L)).thenReturn(List.of(shortageSummary));

        List<AttendanceDto.SummaryResponse> result = attendanceService.getShortageListByCourse(200L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getShortageFlag());
    }
}