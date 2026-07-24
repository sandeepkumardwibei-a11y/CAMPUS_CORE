package com.campuscore.service;

import com.campuscore.dto.NotificationDto;
import com.campuscore.dto.SemesterRegistrationDto;
import com.campuscore.entity.Course;
import com.campuscore.entity.Program;
import com.campuscore.entity.SemesterRegistration;
import com.campuscore.entity.User;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.exception.SemesterRegistrationException;
import com.campuscore.repository.CourseRepository;
import com.campuscore.repository.ProgramRepository;
import com.campuscore.repository.SemesterRegistrationRepository;
import com.campuscore.repository.TimetableRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SemesterRegistrationServiceTest {

    @Mock
    private SemesterRegistrationRepository registrationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private TimetableRepository timetableRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SemesterRegistrationService registrationService;

    private User studentUser;
    private User otherStudentUser;
    private User adminUser;
    private Program program;
    private Course course1;
    private Course course2;
    private SemesterRegistration registration;

    @BeforeEach
    void setUp() {
        // Setup Security Context Mocking
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("student@campus.com");

        studentUser = User.builder()
                .userId(1L)
                .email("student@campus.com")
                .name("Jane Student")
                .role(User.Role.STUDENT)
                .build();

        otherStudentUser = User.builder()
                .userId(2L)
                .email("otherstudent@campus.com")
                .name("John Student")
                .role(User.Role.STUDENT)
                .build();

        adminUser = User.builder()
                .userId(3L)
                .email("admin@campus.com")
                .name("System Admin")
                .role(User.Role.ADMIN)
                .build();

        program = Program.builder()
                .programId(10L)
                .programName("Bachelor of Science")
                .build();

        course1 = Course.builder()
                .courseId(101L)
                .courseName("CS101 - Intro to CS")
                .courseCode("CS101")
                .semester(1)
                .credits(4)
                .status(Course.CourseStatus.ACTIVE)
                .program(program)
                .programIds(List.of(10L))
                .build();

        course2 = Course.builder()
                .courseId(102L)
                .courseName("CS102 - Data Structures")
                .courseCode("CS102")
                .semester(1)
                .credits(3)
                .status(Course.CourseStatus.ACTIVE)
                .program(program)
                .programIds(List.of(10L))
                .build();

        registration = SemesterRegistration.builder()
                .registrationId(50L)
                .student(studentUser)
                .program(program)
                .academicYear("2026")
                .semester(1)
                .courses(new HashSet<>(Set.of(course1, course2)))
                .totalCredits(7)
                .status(SemesterRegistration.RegistrationStatus.REGISTERED)
                .build();

        when(userRepository.findByEmail("student@campus.com")).thenReturn(Optional.of(studentUser));
        when(userRepository.findByEmail("admin@campus.com")).thenReturn(Optional.of(adminUser));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────
    // 1. REGISTER TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void register_Success_ExplicitCourses() {
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1L);
        request.setProgramId(10L);
        request.setAcademicYear("2026");
        request.setSemester(1);
        request.setCourseIds(List.of(101L, 102L));

        when(userRepository.findById(1L)).thenReturn(Optional.of(studentUser));
        when(registrationRepository.findByStudentUserId(1L)).thenReturn(List.of());
        when(registrationRepository.findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(1L, 10L, "2026", 1))
                .thenReturn(Optional.empty());
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        when(courseRepository.findById(101L)).thenReturn(Optional.of(course1));
        when(courseRepository.findById(102L)).thenReturn(Optional.of(course2));
        when(registrationRepository.save(any(SemesterRegistration.class))).thenAnswer(i -> {
            SemesterRegistration r = i.getArgument(0);
            r.setRegistrationId(50L);
            return r;
        });

        SemesterRegistrationDto.Response response = registrationService.register(request);

        assertNotNull(response);
        assertEquals(50L, response.getRegistrationId());
        assertEquals(1L, response.getStudentId());
        assertEquals("REGISTERED", response.getStatus());
        assertEquals(7, response.getTotalCredits());
        assertEquals(2, response.getCourses().size());

        verify(registrationRepository, times(1)).save(any(SemesterRegistration.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void register_Success_FallbackAutoFetchCourses() {
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1L);
        request.setProgramId(10L);
        request.setAcademicYear("2026");
        request.setSemester(1);
        request.setCourseIds(null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(studentUser));
        when(registrationRepository.findByStudentUserId(1L)).thenReturn(List.of());
        when(registrationRepository.findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(1L, 10L, "2026", 1))
                .thenReturn(Optional.empty());
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        when(courseRepository.findAll()).thenReturn(List.of(course1, course2));
        when(registrationRepository.save(any(SemesterRegistration.class))).thenAnswer(i -> {
            SemesterRegistration r = i.getArgument(0);
            r.setRegistrationId(50L);
            return r;
        });

        SemesterRegistrationDto.Response response = registrationService.register(request);

        assertNotNull(response);
        assertEquals(7, response.getTotalCredits());
        verify(courseRepository, times(1)).findAll();
    }

    @Test
    void register_ThrowsException_WhenNonStudentAttemptsRegistration() {
        when(authentication.getName()).thenReturn("admin@campus.com");

        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(3L);
        request.setProgramId(10L);
        request.setAcademicYear("2026");
        request.setSemester(1);

        when(userRepository.findById(3L)).thenReturn(Optional.of(adminUser));

        SemesterRegistrationException ex = assertThrows(SemesterRegistrationException.class,
                () -> registrationService.register(request));
        assertTrue(ex.getMessage().contains("does not belong to a student account"));
    }

    @Test
    void register_ThrowsException_WhenRegisteringForAnotherStudent() {
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(2L);
        request.setProgramId(10L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(otherStudentUser));

        SemesterRegistrationException ex = assertThrows(SemesterRegistrationException.class,
                () -> registrationService.register(request));
        assertTrue(ex.getMessage().contains("You can only register a semester for your own account"));
    }

    @Test
    void register_ThrowsException_WhenActiveRegistrationAlreadyExists() {
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1L);
        request.setProgramId(10L);
        request.setAcademicYear("2026");
        request.setSemester(2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(studentUser));
        when(registrationRepository.findByStudentUserId(1L)).thenReturn(List.of(registration));

        SemesterRegistrationException ex = assertThrows(SemesterRegistrationException.class,
                () -> registrationService.register(request));
        assertTrue(ex.getMessage().contains("You are already registered for Semester"));
    }

    @Test
    void register_ThrowsException_WhenCourseSemesterMismatch() {
        Course courseWrongSem = Course.builder()
                .courseId(103L)
                .courseName("CS201 - Advanced Java")
                .semester(2)
                .credits(3)
                .program(program)
                .status(Course.CourseStatus.ACTIVE)
                .build();

        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1L);
        request.setProgramId(10L);
        request.setAcademicYear("2026");
        request.setSemester(1);
        request.setCourseIds(List.of(103L));

        when(userRepository.findById(1L)).thenReturn(Optional.of(studentUser));
        when(registrationRepository.findByStudentUserId(1L)).thenReturn(List.of());
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        when(courseRepository.findById(103L)).thenReturn(Optional.of(courseWrongSem));

        SemesterRegistrationException ex = assertThrows(SemesterRegistrationException.class,
                () -> registrationService.register(request));
        assertTrue(ex.getMessage().contains("cannot be mixed into a Semester 1 registration"));
    }

    // ─────────────────────────────────────────────────────────
    // 2. READ / QUERY TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getById_Success() {
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));

        SemesterRegistrationDto.Response response = registrationService.getById(50L);

        assertNotNull(response);
        assertEquals(50L, response.getRegistrationId());
        assertEquals("Jane Student", response.getStudentName());
    }

    @Test
    void getById_ThrowsException_WhenNotFound() {
        when(registrationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> registrationService.getById(99L));
    }

    @Test
    void getByStudent_Success() {
        when(registrationRepository.findByStudentUserId(1L)).thenReturn(List.of(registration));

        List<SemesterRegistrationDto.Response> responses = registrationService.getByStudent(1L);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(50L, responses.get(0).getRegistrationId());
    }

    @Test
    void getByCourse_Success() {
        when(courseRepository.findById(101L)).thenReturn(Optional.of(course1));
        when(registrationRepository.findByCoursesCourseId(101L)).thenReturn(List.of(registration));

        List<SemesterRegistrationDto.Response> responses = registrationService.getByCourse(101L);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(1, responses.get(0).getCourses().size());
        assertEquals("CS101 - Intro to CS", responses.get(0).getCourses().get(0).getCourseName());
    }

    @Test
    void getAll_Success() {
        when(registrationRepository.findAll()).thenReturn(List.of(registration));

        List<SemesterRegistrationDto.Response> responses = registrationService.getAll();

        assertNotNull(responses);
        assertEquals(1, responses.size());
    }

    // ─────────────────────────────────────────────────────────
    // 3. CONFIRM REGISTRATION TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void confirmRegistration_Success() {
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(SemesterRegistration.class))).thenAnswer(i -> i.getArgument(0));

        SemesterRegistrationDto.Response response = registrationService.confirmRegistration(50L);

        assertNotNull(response);
        assertEquals("CONFIRMED", response.getStatus());
        verify(registrationRepository, times(1)).save(registration);
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }
}