package com.campuscore.service;

import com.campuscore.dto.NotificationDto;
import com.campuscore.dto.TimetableDto;
import com.campuscore.entity.Course;
import com.campuscore.entity.SemesterRegistration;
import com.campuscore.entity.Timetable;
import com.campuscore.entity.User;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.exception.TimetableException;
import com.campuscore.repository.CourseRepository;
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

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimetableServiceTest {

    @Mock
    private TimetableRepository timetableRepository;

    @Mock
    private SemesterRegistrationRepository registrationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private TimetableService timetableService;

    private User studentUser;
    private User otherStudentUser;
    private User adminUser;
    private Course course1;
    private Course course2;
    private Timetable timetable1;
    private SemesterRegistration registration;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin@campus.com");

        adminUser = User.builder()
                .userId(1L)
                .email("admin@campus.com")
                .role(User.Role.ADMIN)
                .build();

        studentUser = User.builder()
                .userId(2L)
                .email("student@campus.com")
                .role(User.Role.STUDENT)
                .build();

        otherStudentUser = User.builder()
                .userId(3L)
                .email("otherstudent@campus.com")
                .role(User.Role.STUDENT)
                .build();

        course1 = Course.builder()
                .courseId(101L)
                .courseCode("CS101")
                .courseName("Intro to Programming")
                .semester(1)
                .build();

        course2 = Course.builder()
                .courseId(102L)
                .courseCode("CS102")
                .courseName("Data Structures")
                .semester(1)
                .build();

        timetable1 = Timetable.builder()
                .timetableId(1L)
                .course(course1)
                .dayOfWeek(Timetable.DayOfWeek.MONDAY)
                .startTime(LocalTime.of(8, 30))
                .endTime(LocalTime.of(9, 30))
                .venue("Hall A")
                .academicYear("2026")
                .semester(1)
                .build();

        registration = SemesterRegistration.builder()
                .registrationId(10L)
                .student(studentUser)
                .academicYear("2026")
                .semester(1)
                .courses(Set.of(course1))
                .build();

        when(userRepository.findByEmail("admin@campus.com")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmail("student@campus.com")).thenReturn(Optional.of(studentUser));
        when(userRepository.findByEmail("otherstudent@campus.com")).thenReturn(Optional.of(otherStudentUser));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────
    // 1. CREATE SLOT TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void createSlot_Success() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setCourseId(101L);
        request.setDayOfWeek("MONDAY");
        request.setStartTime(LocalTime.of(8, 30));
        request.setEndTime(LocalTime.of(9, 30));
        request.setVenue("Hall A");
        request.setAcademicYear("2026");
        request.setSemester(1);

        when(courseRepository.findById(101L)).thenReturn(Optional.of(course1));
        when(timetableRepository.findAll()).thenReturn(List.of());
        when(registrationRepository.findByCoursesCourseId(101L)).thenReturn(List.of(registration));
        when(timetableRepository.save(any(Timetable.class))).thenAnswer(i -> {
            Timetable t = i.getArgument(0);
            t.setTimetableId(100L);
            return t;
        });

        TimetableDto.Response response = timetableService.createSlot(request);

        assertNotNull(response);
        assertEquals(100L, response.getTimetableId());
        assertEquals("CS101", response.getCourseCode());
        assertEquals("MONDAY", response.getDayOfWeek());

        verify(timetableRepository, times(1)).save(any(Timetable.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void createSlot_ThrowsException_WhenStartTimeAfterEndTime() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setStartTime(LocalTime.of(10, 0));
        request.setEndTime(LocalTime.of(9, 0));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("must be strictly before the end time"));
    }

    @Test
    void createSlot_ThrowsException_WhenOutsideCollegeHours() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setStartTime(LocalTime.of(7, 30)); // College opens at 08:00
        request.setEndTime(LocalTime.of(8, 30));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("must be scheduled within college hours"));
    }

    @Test
    void createSlot_ThrowsException_WhenOverlappingBreakWindow() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setStartTime(LocalTime.of(12, 15)); // Lunch Break is 12:00–13:00
        request.setEndTime(LocalTime.of(13, 15));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("overlaps a scheduled break"));
    }

    @Test
    void createSlot_ThrowsException_WhenCourseNotFound() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setCourseId(999L);
        request.setStartTime(LocalTime.of(8, 30));
        request.setEndTime(LocalTime.of(9, 30));

        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> timetableService.createSlot(request));
    }

    @Test
    void createSlot_ThrowsException_WhenCourseSemesterMismatch() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setCourseId(101L); // Course is configured for Semester 1
        request.setStartTime(LocalTime.of(8, 30));
        request.setEndTime(LocalTime.of(9, 30));
        request.setSemester(2); // Requesting Semester 2 assignment

        when(courseRepository.findById(101L)).thenReturn(Optional.of(course1));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("belongs to Semester 1"));
    }

    @Test
    void createSlot_ThrowsException_WhenTimeCollisionWithExistingSlot() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setCourseId(101L);
        request.setDayOfWeek("MONDAY");
        request.setStartTime(LocalTime.of(9, 0)); // Overlaps with existing timetable1 (08:30 - 09:30)
        request.setEndTime(LocalTime.of(10, 0));
        request.setSemester(1);

        when(courseRepository.findById(101L)).thenReturn(Optional.of(course1));
        when(timetableRepository.findAll()).thenReturn(List.of(timetable1));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("Scheduling Conflict"));
    }

    // ─────────────────────────────────────────────────────────
    // 2. READ / QUERY TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getAllSlots_Success() {
        when(timetableRepository.findAll()).thenReturn(List.of(timetable1));

        List<TimetableDto.Response> responses = timetableService.getAllSlots();

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("CS101", responses.get(0).getCourseCode());
    }

    @Test
    void getSlotsByCourse_Success_ForStudentRegisteredInCourse() {
        when(authentication.getName()).thenReturn("student@campus.com");
        when(registrationRepository.findByStudentUserId(2L)).thenReturn(List.of(registration));
        when(timetableRepository.findByCourse_CourseId(101L)).thenReturn(List.of(timetable1));

        List<TimetableDto.Response> responses = timetableService.getSlotsByCourse(101L);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(101L, responses.get(0).getCourseId());
    }

    @Test
    void getSlotsByCourse_ThrowsException_WhenStudentNotRegisteredInCourse() {
        when(authentication.getName()).thenReturn("student@campus.com");
        when(registrationRepository.findByStudentUserId(2L)).thenReturn(List.of()); // No registrations

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.getSlotsByCourse(101L));
        assertTrue(ex.getMessage().contains("You cannot view the timetable for a course you are not registered in"));
    }

    @Test
    void getStudentSchedule_Success() {
        when(authentication.getName()).thenReturn("student@campus.com");
        when(registrationRepository.findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(2L, 10L, "2026", 1))
                .thenReturn(Optional.of(registration));
        when(timetableRepository.findByCourse_CourseId(101L)).thenReturn(List.of(timetable1));

        List<TimetableDto.Response> responses = timetableService.getStudentSchedule(2L, 10L, "2026", 1);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("CS101", responses.get(0).getCourseCode());
    }

    @Test
    void getStudentSchedule_ThrowsException_WhenStudentAccessesAnotherStudentsSchedule() {
        when(authentication.getName()).thenReturn("student@campus.com"); // Authenticated user ID is 2

        TimetableException ex = assertThrows(TimetableException.class,
                () -> timetableService.getStudentSchedule(3L, 10L, "2026", 1)); // Accessing student ID 3

        assertTrue(ex.getMessage().contains("You are not authorized to view another student's timetable agenda"));
    }

    @Test
    void getStudentSchedule_ThrowsException_WhenNoActiveRegistrationFound() {
        when(authentication.getName()).thenReturn("admin@campus.com");
        when(registrationRepository.findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(2L, 10L, "2026", 1))
                .thenReturn(Optional.empty());

        TimetableException ex = assertThrows(TimetableException.class,
                () -> timetableService.getStudentSchedule(2L, 10L, "2026", 1));

        assertTrue(ex.getMessage().contains("Active registration records not found"));
    }
}