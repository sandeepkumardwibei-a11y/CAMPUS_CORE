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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    private User sampleStudent;
    private User currentUserSession;
    private Program sampleProgram;
    private Course sampleCourse;
    private SemesterRegistration sampleRegistration;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        sampleStudent = new User();
        sampleStudent.setUserId(1000L);
        sampleStudent.setName("Michael Scott");
        sampleStudent.setEmail("michael@campuscore.com");
        sampleStudent.setRole(User.Role.STUDENT);

        currentUserSession = new User();
        currentUserSession.setUserId(1000L);
        currentUserSession.setEmail("michael@campuscore.com");
        currentUserSession.setRole(User.Role.STUDENT);

        sampleProgram = new Program();
        sampleProgram.setProgramId(50L);
        sampleProgram.setProgramName("Business Administration");

        sampleCourse = new Course();
        sampleCourse.setCourseId(200L);
        sampleCourse.setCourseName("Intro to Management");
        sampleCourse.setCourseCode("MGMT101");
        sampleCourse.setProgram(sampleProgram);
        sampleCourse.setProgramIds(List.of(50L));
        sampleCourse.setSemester(1);
        sampleCourse.setCredits(4);
        sampleCourse.setStatus(Course.CourseStatus.ACTIVE);

        Set<Course> courseSet = new HashSet<>();
        courseSet.add(sampleCourse);

        sampleRegistration = new SemesterRegistration();
        sampleRegistration.setRegistrationId(5000L);
        sampleRegistration.setStudent(sampleStudent);
        sampleRegistration.setProgram(sampleProgram);
        sampleRegistration.setAcademicYear("2026-27");
        sampleRegistration.setSemester(1);
        sampleRegistration.setCourses(courseSet);
        sampleRegistration.setTotalCredits(4);
        sampleRegistration.setStatus(SemesterRegistration.RegistrationStatus.REGISTERED);
    }

    private void mockSecurityContext(String username) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(username);
    }

    // ─────────────────────────────────────────────────────────
    // REGISTER OPERATION TEST CASES
    // ─────────────────────────────────────────────────────────

    @Test
    void register_SuccessWithExplicitCourseIds() {
        mockSecurityContext("michael@campuscore.com");
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1000L);
        request.setProgramId(50L);
        request.setAcademicYear("2026-27");
        request.setSemester(1);
        request.setCourseIds(List.of(200L));

        when(userRepository.findById(1000L)).thenReturn(Optional.of(sampleStudent));
        when(userRepository.findByEmail("michael@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(registrationRepository.findByStudentUserId(1000L)).thenReturn(Collections.emptyList());
        when(registrationRepository.findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(1000L, 50L, "2026-27", 1))
                .thenReturn(Optional.empty());
        when(programRepository.findById(50L)).thenReturn(Optional.of(sampleProgram));
        when(courseRepository.findById(200L)).thenReturn(Optional.of(sampleCourse));

        when(registrationRepository.save(any(SemesterRegistration.class))).thenAnswer(invocation -> {
            SemesterRegistration reg = invocation.getArgument(0);
            reg.setRegistrationId(5000L);
            return reg;
        });

        SemesterRegistrationDto.Response response = registrationService.register(request);

        assertNotNull(response);
        assertEquals(5000L, response.getRegistrationId());
        assertEquals("REGISTERED", response.getStatus());
        assertEquals(4, response.getTotalCredits());
        verify(registrationRepository).save(any(SemesterRegistration.class));
        verify(eventPublisher).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void register_SuccessWithAutoFetchDefaultCatalogue() {
        mockSecurityContext("michael@campuscore.com");
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1000L);
        request.setProgramId(50L);
        request.setAcademicYear("2026-27");
        request.setSemester(1);
        request.setCourseIds(null); // Triggers auto-fetch logic path

        when(userRepository.findById(1000L)).thenReturn(Optional.of(sampleStudent));
        when(userRepository.findByEmail("michael@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(registrationRepository.findByStudentUserId(1000L)).thenReturn(Collections.emptyList());
        when(registrationRepository.findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(1000L, 50L, "2026-27", 1))
                .thenReturn(Optional.empty());
        when(programRepository.findById(50L)).thenReturn(Optional.of(sampleProgram));
        when(courseRepository.findAll()).thenReturn(Collections.singletonList(sampleCourse));

        when(registrationRepository.save(any(SemesterRegistration.class))).thenAnswer(invocation -> {
            SemesterRegistration reg = invocation.getArgument(0);
            reg.setRegistrationId(5000L);
            return reg;
        });

        SemesterRegistrationDto.Response response = registrationService.register(request);

        assertNotNull(response);
        assertEquals(1, response.getCourses().size());
        assertEquals("Intro to Management", response.getCourses().get(0).getCourseName());
        verify(eventPublisher).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void register_ThrowsException_WhenUserIsNotStudent() {
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1000L);
        sampleStudent.setRole(User.Role.FACULTY); // Invalid role

        when(userRepository.findById(1000L)).thenReturn(Optional.of(sampleStudent));

        assertThrows(SemesterRegistrationException.class, () -> registrationService.register(request));
    }

    @Test
    void register_ThrowsException_WhenSemesterOutOfRange() {
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1000L);
        request.setSemester(9); // Semester must be between 1 and 8

        when(userRepository.findById(1000L)).thenReturn(Optional.of(sampleStudent));

        SemesterRegistrationException ex = assertThrows(SemesterRegistrationException.class, () -> registrationService.register(request));
        assertTrue(ex.getMessage().contains("Semester must be between 1 and 8"));
    }

    @Test
    void register_ThrowsException_WhenNonStudentAttemptsRegistration() {
        mockSecurityContext("faculty@campuscore.com");
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1000L);
        request.setSemester(1);

        User facultySession = new User();
        facultySession.setUserId(2000L);
        facultySession.setEmail("faculty@campuscore.com");
        facultySession.setRole(User.Role.FACULTY);

        when(userRepository.findById(1000L)).thenReturn(Optional.of(sampleStudent));
        when(userRepository.findByEmail("faculty@campuscore.com")).thenReturn(Optional.of(facultySession));

        SemesterRegistrationException ex = assertThrows(SemesterRegistrationException.class, () -> registrationService.register(request));
        assertTrue(ex.getMessage().contains("Only a student can create a semester registration"));
    }

    @Test
    void register_ThrowsException_WhenStudentRegistersForAnotherStudent() {
        mockSecurityContext("michael@campuscore.com");
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(9999L); // Target student ID differs from logged in user ID (1000L)
        request.setSemester(1);

        User targetStudent = new User();
        targetStudent.setUserId(9999L);
        targetStudent.setRole(User.Role.STUDENT);

        when(userRepository.findById(9999L)).thenReturn(Optional.of(targetStudent));
        when(userRepository.findByEmail("michael@campuscore.com")).thenReturn(Optional.of(currentUserSession));

        SemesterRegistrationException ex = assertThrows(SemesterRegistrationException.class, () -> registrationService.register(request));
        assertTrue(ex.getMessage().contains("You can only register a semester for your own account"));
    }

    @Test
    void register_ThrowsException_WhenActiveSemesterAlreadyExists() {
        mockSecurityContext("michael@campuscore.com");
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1000L);
        request.setSemester(2);

        when(userRepository.findById(1000L)).thenReturn(Optional.of(sampleStudent));
        when(userRepository.findByEmail("michael@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(registrationRepository.findByStudentUserId(1000L)).thenReturn(Collections.singletonList(sampleRegistration));

        SemesterRegistrationException ex = assertThrows(SemesterRegistrationException.class, () -> registrationService.register(request));
        assertTrue(ex.getMessage().contains("A student can only be registered for one semester at a time"));
    }

    @Test
    void register_ThrowsException_WhenCourseDoesNotBelongToProgram() {
        mockSecurityContext("michael@campuscore.com");
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1000L);
        request.setProgramId(99L); // Course programId is 50L
        request.setAcademicYear("2026-27");
        request.setSemester(1);
        request.setCourseIds(List.of(200L));

        Program wrongProgram = new Program();
        wrongProgram.setProgramId(99L);
        wrongProgram.setProgramName("Fine Arts");

        when(userRepository.findById(1000L)).thenReturn(Optional.of(sampleStudent));
        when(userRepository.findByEmail("michael@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(registrationRepository.findByStudentUserId(1000L)).thenReturn(Collections.emptyList());
        when(registrationRepository.findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(1000L, 99L, "2026-27", 1))
                .thenReturn(Optional.empty());
        when(programRepository.findById(99L)).thenReturn(Optional.of(wrongProgram));
        when(courseRepository.findById(200L)).thenReturn(Optional.of(sampleCourse));

        SemesterRegistrationException ex = assertThrows(SemesterRegistrationException.class, () -> registrationService.register(request));
        assertTrue(ex.getMessage().contains("does not belong to the requested registration program"));
    }

    @Test
    void register_ThrowsException_WhenCourseBelongsToDifferentSemester() {
        mockSecurityContext("michael@campuscore.com");
        SemesterRegistrationDto.CreateRequest request = new SemesterRegistrationDto.CreateRequest();
        request.setStudentId(1000L);
        request.setProgramId(50L);
        request.setAcademicYear("2026-27");
        request.setSemester(2); // Requested Semester is 2
        request.setCourseIds(List.of(200L));

        sampleCourse.setSemester(1); // Course explicitly tagged to Semester 1

        when(userRepository.findById(1000L)).thenReturn(Optional.of(sampleStudent));
        when(userRepository.findByEmail("michael@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(registrationRepository.findByStudentUserId(1000L)).thenReturn(Collections.emptyList());
        when(registrationRepository.findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(1000L, 50L, "2026-27", 2))
                .thenReturn(Optional.empty());
        when(programRepository.findById(50L)).thenReturn(Optional.of(sampleProgram));
        when(courseRepository.findById(200L)).thenReturn(Optional.of(sampleCourse));

        SemesterRegistrationException ex = assertThrows(SemesterRegistrationException.class, () -> registrationService.register(request));
        assertTrue(ex.getMessage().contains("It cannot be mixed into a Semester 2 registration"));
    }

    // ─────────────────────────────────────────────────────────
    // CONFIRMATION AND READ OPERATIONS TEST CASES
    // ─────────────────────────────────────────────────────────

    @Test
    void confirmRegistration_Success() {
        when(registrationRepository.findById(5000L)).thenReturn(Optional.of(sampleRegistration));
        when(registrationRepository.save(any(SemesterRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SemesterRegistrationDto.Response response = registrationService.confirmRegistration(5000L);

        assertNotNull(response);
        assertEquals("CONFIRMED", response.getStatus());
        verify(eventPublisher).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void getById_Success() {
        mockSecurityContext("michael@campuscore.com");
        when(registrationRepository.findById(5000L)).thenReturn(Optional.of(sampleRegistration));
        when(userRepository.findByEmail("michael@campuscore.com")).thenReturn(Optional.of(currentUserSession));

        SemesterRegistrationDto.Response response = registrationService.getById(5000L);

        assertNotNull(response);
        assertEquals(5000L, response.getRegistrationId());
    }

    @Test
    void getByStudent_Success() {
        mockSecurityContext("michael@campuscore.com");
        when(userRepository.findByEmail("michael@campuscore.com")).thenReturn(Optional.of(currentUserSession));
        when(registrationRepository.findByStudentUserId(1000L)).thenReturn(Collections.singletonList(sampleRegistration));

        List<SemesterRegistrationDto.Response> results = registrationService.getByStudent(1000L);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(5000L, results.get(0).getRegistrationId());
    }

    @Test
    void getByCourse_Success() {
        when(courseRepository.findById(200L)).thenReturn(Optional.of(sampleCourse));
        when(registrationRepository.findByCoursesCourseId(200L)).thenReturn(Collections.singletonList(sampleRegistration));

        List<SemesterRegistrationDto.Response> results = registrationService.getByCourse(200L);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).getCourses().size());
    }

    @Test
    void getByCourse_ThrowsException_WhenCourseNotFound() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> registrationService.getByCourse(999L));
    }

    @Test
    void getAll_Success() {
        when(registrationRepository.findAll()).thenReturn(Collections.singletonList(sampleRegistration));

        List<SemesterRegistrationDto.Response> results = registrationService.getAll();

        assertNotNull(results);
        assertEquals(1, results.size());
    }
}