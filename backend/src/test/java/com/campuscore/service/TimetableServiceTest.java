package com.campuscore.service;

import com.campuscore.dto.NotificationDto;
import com.campuscore.dto.TimetableDto;
import com.campuscore.entity.Course;
import com.campuscore.entity.Program;
import com.campuscore.entity.SemesterRegistration;
import com.campuscore.entity.Timetable;
import com.campuscore.entity.User;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.exception.TimetableException;
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

import java.time.LocalTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
    private ProgramRepository programRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private TimetableService timetableService;

    private User sampleStudent;
    private User sampleFaculty;
    private Program sampleProgram;
    private Course sampleCourse;
    private Timetable sampleTimetable;
    private SemesterRegistration sampleRegistration;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        sampleStudent = new User();
        sampleStudent.setUserId(800L);
        sampleStudent.setName("John Doe");
        sampleStudent.setEmail("john@campuscore.com");
        sampleStudent.setRole(User.Role.STUDENT);

        sampleFaculty = new User();
        sampleFaculty.setUserId(900L);
        sampleFaculty.setName("Dr. Alan Turing");
        sampleFaculty.setEmail("alan@campuscore.com");
        sampleFaculty.setRole(User.Role.FACULTY);

        sampleProgram = new Program();
        sampleProgram.setProgramId(10L);
        sampleProgram.setProgramName("B.Tech CS");

        sampleCourse = new Course();
        sampleCourse.setCourseId(300L);
        sampleCourse.setCourseCode("CS101");
        sampleCourse.setCourseName("Computer Science I");
        sampleCourse.setSemester(1);
        sampleCourse.setProgramIds(List.of(10L));
        sampleCourse.setFaculty(sampleFaculty);

        sampleTimetable = new Timetable();
        sampleTimetable.setTimetableId(7000L);
        sampleTimetable.setCourse(sampleCourse);
        sampleTimetable.setProgramId(10L);
        sampleTimetable.setDayOfWeek(Timetable.DayOfWeek.MONDAY);
        sampleTimetable.setStartTime(LocalTime.of(8, 30));
        sampleTimetable.setEndTime(LocalTime.of(9, 30));
        sampleTimetable.setVenue("Hall 101");
        sampleTimetable.setAcademicYear("2026-27");
        sampleTimetable.setSemester(1);

        Set<Course> courseSet = new HashSet<>();
        courseSet.add(sampleCourse);

        sampleRegistration = new SemesterRegistration();
        sampleRegistration.setRegistrationId(555L);
        sampleRegistration.setStudent(sampleStudent);
        sampleRegistration.setProgram(sampleProgram);
        sampleRegistration.setAcademicYear("2026-27");
        sampleRegistration.setSemester(1);
        sampleRegistration.setCourses(courseSet);
        sampleRegistration.setStatus(SemesterRegistration.RegistrationStatus.REGISTERED);
    }

    private void mockSecurityContext(String username) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(username);
    }

    // ─────────────────────────────────────────────────────────
    // CREATE SLOT VALIDATION & SUCCESS TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void createSlot_Success() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setCourseId(300L);
        request.setProgramId(10L);
        request.setDayOfWeek("MONDAY");
        request.setStartTime(LocalTime.of(8, 30));
        request.setEndTime(LocalTime.of(9, 30));
        request.setVenue("Hall 101");
        request.setAcademicYear("2026-27");
        request.setSemester(1);

        when(courseRepository.findById(300L)).thenReturn(Optional.of(sampleCourse));
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));
        when(timetableRepository.findAll()).thenReturn(Collections.emptyList());
        when(registrationRepository.findByCoursesCourseId(300L)).thenReturn(Collections.singletonList(sampleRegistration));

        when(timetableRepository.save(any(Timetable.class))).thenAnswer(invocation -> {
            Timetable t = invocation.getArgument(0);
            t.setTimetableId(7000L);
            return t;
        });

        TimetableDto.Response response = timetableService.createSlot(request);

        assertNotNull(response);
        assertEquals(7000L, response.getTimetableId());
        assertEquals("MONDAY", response.getDayOfWeek());
        assertEquals("B.Tech CS", response.getProgramName());
        verify(timetableRepository).save(any(Timetable.class));
        verify(eventPublisher).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void createSlot_ThrowsException_WhenStartTimeNotBeforeEndTime() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setStartTime(LocalTime.of(10, 0));
        request.setEndTime(LocalTime.of(9, 0));

        assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
    }

    @Test
    void createSlot_ThrowsException_WhenOutsideCollegeHours() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setStartTime(LocalTime.of(7, 30)); // Before 08:00
        request.setEndTime(LocalTime.of(8, 30));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("college hours"));
    }

    @Test
    void createSlot_ThrowsException_WhenOverlapsBreakWindow() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setStartTime(LocalTime.of(9, 30));
        request.setEndTime(LocalTime.of(10, 10)); // Overlaps morning break 10:00–10:15

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("overlaps a scheduled break"));
    }

    @Test
    void createSlot_ThrowsException_WhenSemesterOutOfRange() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setStartTime(LocalTime.of(8, 30));
        request.setEndTime(LocalTime.of(9, 30));
        request.setSemester(9); // Semester must be 1-8

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("Semester must be between 1 and 8"));
    }

    @Test
    void createSlot_ThrowsException_WhenProgramNotSelected() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setCourseId(300L);
        request.setStartTime(LocalTime.of(8, 30));
        request.setEndTime(LocalTime.of(9, 30));
        request.setSemester(1);
        request.setProgramId(null);

        when(courseRepository.findById(300L)).thenReturn(Optional.of(sampleCourse));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("select a program"));
    }

    @Test
    void createSlot_ThrowsException_WhenCourseNotOfferedInProgram() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setCourseId(300L);
        request.setProgramId(20L); // Program 20 is not in sampleCourse.programIds (which has 10L)
        request.setStartTime(LocalTime.of(8, 30));
        request.setEndTime(LocalTime.of(9, 30));
        request.setSemester(1);

        Program otherProgram = new Program();
        otherProgram.setProgramId(20L);
        otherProgram.setProgramName("B.Tech IT");

        when(courseRepository.findById(300L)).thenReturn(Optional.of(sampleCourse));
        when(programRepository.findById(20L)).thenReturn(Optional.of(otherProgram));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("is not offered under the program"));
    }

    @Test
    void createSlot_ThrowsException_WhenCourseSemesterMismatch() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setCourseId(300L);
        request.setProgramId(10L);
        request.setStartTime(LocalTime.of(8, 30));
        request.setEndTime(LocalTime.of(9, 30));
        request.setSemester(2); // sampleCourse is semester 1

        when(courseRepository.findById(300L)).thenReturn(Optional.of(sampleCourse));
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("belongs to Semester 1"));
    }

    @Test
    void createSlot_ThrowsException_WhenVenueConflict() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setCourseId(300L);
        request.setProgramId(10L);
        request.setDayOfWeek("MONDAY");
        request.setStartTime(LocalTime.of(8, 30));
        request.setEndTime(LocalTime.of(9, 30));
        request.setVenue("Hall 101"); // Same venue as sampleTimetable
        request.setSemester(1);

        when(courseRepository.findById(300L)).thenReturn(Optional.of(sampleCourse));
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));
        when(timetableRepository.findAll()).thenReturn(Collections.singletonList(sampleTimetable));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("Venue 'Hall 101' is already booked"));
    }

    @Test
    void createSlot_ThrowsException_WhenFacultyConflict() {
        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setCourseId(300L);
        request.setProgramId(10L);
        request.setDayOfWeek("MONDAY");
        request.setStartTime(LocalTime.of(8, 30));
        request.setEndTime(LocalTime.of(9, 30));
        request.setVenue("Hall 202"); // Different venue, but same faculty (sampleFaculty)
        request.setSemester(1);

        // Modify sampleTimetable to have a different venue so venue check passes
        sampleTimetable.setVenue("Hall 101");

        when(courseRepository.findById(300L)).thenReturn(Optional.of(sampleCourse));
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));
        when(timetableRepository.findAll()).thenReturn(Collections.singletonList(sampleTimetable));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("Faculty 'Dr. Alan Turing' is already teaching"));
    }

    @Test
    void createSlot_ThrowsException_WhenProgramConflict() {
        // Create another course with a different faculty
        User otherFaculty = new User();
        otherFaculty.setUserId(999L);
        otherFaculty.setName("Dr. Ada Lovelace");

        Course otherCourse = new Course();
        otherCourse.setCourseId(400L);
        otherCourse.setCourseCode("CS102");
        otherCourse.setCourseName("Data Structures");
        otherCourse.setSemester(1);
        otherCourse.setProgramIds(List.of(10L));
        otherCourse.setFaculty(otherFaculty);

        TimetableDto.CreateRequest request = new TimetableDto.CreateRequest();
        request.setCourseId(400L);
        request.setProgramId(10L); // Same program ID 10L as sampleTimetable
        request.setDayOfWeek("MONDAY");
        request.setStartTime(LocalTime.of(8, 30));
        request.setEndTime(LocalTime.of(9, 30));
        request.setVenue("Hall 202"); // Different venue
        request.setSemester(1);

        when(courseRepository.findById(400L)).thenReturn(Optional.of(otherCourse));
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));
        when(timetableRepository.findAll()).thenReturn(Collections.singletonList(sampleTimetable));

        TimetableException ex = assertThrows(TimetableException.class, () -> timetableService.createSlot(request));
        assertTrue(ex.getMessage().contains("The program 'B.Tech CS' already has a class"));
    }

    // ─────────────────────────────────────────────────────────
    // QUERY ENDPOINTS & SELF-SERVICE TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getAllSlots_Success() {
        when(timetableRepository.findAll()).thenReturn(Collections.singletonList(sampleTimetable));
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));

        List<TimetableDto.Response> results = timetableService.getAllSlots();

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("CS101", results.get(0).getCourseCode());
    }

    @Test
    void getSlotsByCourse_StudentAllowed_WhenRegistered() {
        mockSecurityContext("john@campuscore.com");
        when(userRepository.findByEmail("john@campuscore.com")).thenReturn(Optional.of(sampleStudent));
        when(registrationRepository.findByStudentUserId(800L)).thenReturn(Collections.singletonList(sampleRegistration));
        when(timetableRepository.findByCourse_CourseId(300L)).thenReturn(Collections.singletonList(sampleTimetable));
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));

        List<TimetableDto.Response> results = timetableService.getSlotsByCourse(300L);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void getSlotsByCourse_ThrowsException_WhenStudentNotRegistered() {
        mockSecurityContext("john@campuscore.com");
        when(userRepository.findByEmail("john@campuscore.com")).thenReturn(Optional.of(sampleStudent));
        when(registrationRepository.findByStudentUserId(800L)).thenReturn(Collections.emptyList());

        assertThrows(TimetableException.class, () -> timetableService.getSlotsByCourse(300L));
    }

    @Test
    void getStudentSchedule_SuccessForSelf() {
        mockSecurityContext("john@campuscore.com");
        when(userRepository.findByEmail("john@campuscore.com")).thenReturn(Optional.of(sampleStudent));
        when(registrationRepository.findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(800L, 10L, "2026-27", 1))
                .thenReturn(Optional.of(sampleRegistration));
        when(timetableRepository.findByCourse_CourseId(300L)).thenReturn(Collections.singletonList(sampleTimetable));
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));

        List<TimetableDto.Response> results = timetableService.getStudentSchedule(800L, 10L, "2026-27", 1);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void getStudentSchedule_ThrowsException_WhenAccessingOtherStudentData() {
        mockSecurityContext("john@campuscore.com");
        when(userRepository.findByEmail("john@campuscore.com")).thenReturn(Optional.of(sampleStudent));

        assertThrows(TimetableException.class, () -> timetableService.getStudentSchedule(999L, 10L, "2026-27", 1));
    }

    @Test
    void getMyStudentSchedule_Success() {
        mockSecurityContext("john@campuscore.com");
        when(userRepository.findByEmail("john@campuscore.com")).thenReturn(Optional.of(sampleStudent));
        when(registrationRepository.findByStudentUserId(800L)).thenReturn(Collections.singletonList(sampleRegistration));
        when(timetableRepository.findByCourse_CourseId(300L)).thenReturn(Collections.singletonList(sampleTimetable));
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));

        List<TimetableDto.Response> results = timetableService.getMyStudentSchedule();

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void getMyTeachingSchedule_Success() {
        mockSecurityContext("alan@campuscore.com");
        when(userRepository.findByEmail("alan@campuscore.com")).thenReturn(Optional.of(sampleFaculty));
        when(courseRepository.findByFacultyUserId(900L)).thenReturn(Collections.singletonList(sampleCourse));
        when(timetableRepository.findByCourse_CourseId(300L)).thenReturn(Collections.singletonList(sampleTimetable));
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));

        List<TimetableDto.Response> results = timetableService.getMyTeachingSchedule();

        assertNotNull(results);
        assertEquals(1, results.size());
    }
}