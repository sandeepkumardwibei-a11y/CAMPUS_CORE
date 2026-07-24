package com.campuscore.service;

import com.campuscore.dto.CourseDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.Course;
import com.campuscore.entity.Program;
import com.campuscore.entity.User;
import com.campuscore.exception.CourseException;
import com.campuscore.exception.DuplicateResourceException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.CourseRepository;
import com.campuscore.repository.ProgramRepository;
import com.campuscore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CourseService courseService;

    private User facultyUser;
    private User studentUser;
    private Program sampleProgram;
    private Course sampleCourse;

    @BeforeEach
    void setUp() {
        facultyUser = new User();
        facultyUser.setUserId(10L);
        facultyUser.setName("Prof. Alan");
        facultyUser.setRole(User.Role.FACULTY);

        studentUser = new User();
        studentUser.setUserId(101L);
        studentUser.setName("Alice Smith");
        studentUser.setRole(User.Role.STUDENT);

        sampleProgram = new Program();
        sampleProgram.setProgramId(1L);
        sampleProgram.setProgramName("Computer Science");

        sampleCourse = Course.builder()
                .courseId(200L)
                .courseName("Data Structures")
                .courseCode("CS101")
                .program(sampleProgram)
                .programIds(List.of(1L))
                .semester(1)
                .credits(4)
                .faculty(facultyUser)
                .maxEnrollment(60)
                .status(Course.CourseStatus.ACTIVE)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // 1. CREATE COURSE TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void createCourse_Success() {
        CourseDto.CreateRequest request = new CourseDto.CreateRequest();
        request.setCourseName("Data Structures");
        request.setCourseCode("CS101");
        request.setProgramIds(List.of(1L));
        request.setFacultyId(10L);
        request.setSemester(1);
        request.setCredits(4);
        request.setMaxEnrollment(60);

        when(courseRepository.findByCourseCode("CS101")).thenReturn(Optional.empty());
        when(programRepository.findById(1L)).thenReturn(Optional.of(sampleProgram));
        when(userRepository.findById(10L)).thenReturn(Optional.of(facultyUser));
        when(courseRepository.save(any(Course.class))).thenReturn(sampleCourse);

        CourseDto.Response response = courseService.createCourse(request);

        assertNotNull(response);
        assertEquals(200L, response.getCourseId());
        assertEquals("CS101", response.getCourseCode());
        assertEquals("Prof. Alan", response.getFacultyName());

        verify(courseRepository, times(1)).save(any(Course.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void createCourse_ThrowsException_WhenDuplicateCourseCode() {
        CourseDto.CreateRequest request = new CourseDto.CreateRequest();
        request.setCourseName("Data Structures");
        request.setCourseCode("CS101");

        when(courseRepository.findByCourseCode("CS101")).thenReturn(Optional.of(sampleCourse));

        assertThrows(DuplicateResourceException.class, () -> courseService.createCourse(request));
    }

    @Test
    void createCourse_ThrowsException_WhenFacultyIdNotFacultyRole() {
        CourseDto.CreateRequest request = new CourseDto.CreateRequest();
        request.setCourseName("Data Structures");
        request.setCourseCode("CS101");
        request.setFacultyId(101L); // Non-faculty user

        when(courseRepository.findByCourseCode("CS101")).thenReturn(Optional.empty());
        when(userRepository.findById(101L)).thenReturn(Optional.of(studentUser));

        assertThrows(CourseException.class, () -> courseService.createCourse(request));
    }

    // ─────────────────────────────────────────────────────────
    // 2. GET COURSE TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getById_Success() {
        when(courseRepository.findById(200L)).thenReturn(Optional.of(sampleCourse));
        when(programRepository.findById(1L)).thenReturn(Optional.of(sampleProgram));

        CourseDto.Response response = courseService.getById(200L);

        assertNotNull(response);
        assertEquals(200L, response.getCourseId());
        assertEquals("Data Structures", response.getCourseName());
    }

    @Test
    void getById_ThrowsException_WhenNotFound() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> courseService.getById(999L));
    }

    @Test
    void getByProgram_Success() {
        when(courseRepository.findByProgramProgramId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleCourse)));
        when(courseRepository.findByProgramIdsContaining(1L)).thenReturn(Collections.emptyList());
        when(programRepository.findById(1L)).thenReturn(Optional.of(sampleProgram));

        List<CourseDto.Response> result = courseService.getByProgram(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CS101", result.get(0).getCourseCode());
    }

    @Test
    void getByFaculty_Success() {
        when(courseRepository.findByFacultyUserId(10L)).thenReturn(List.of(sampleCourse));
        when(programRepository.findById(1L)).thenReturn(Optional.of(sampleProgram));

        List<CourseDto.Response> result = courseService.getByFaculty(10L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Prof. Alan", result.get(0).getFacultyName());
    }

    // ─────────────────────────────────────────────────────────
    // 3. ASSIGN FACULTY & UPDATE STATUS TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void assignFaculty_Success() {
        sampleCourse.setFaculty(null);

        when(courseRepository.findById(200L)).thenReturn(Optional.of(sampleCourse));
        when(userRepository.findById(10L)).thenReturn(Optional.of(facultyUser));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(programRepository.findById(1L)).thenReturn(Optional.of(sampleProgram));

        CourseDto.Response response = courseService.assignFaculty(200L, 10L);

        assertNotNull(response);
        assertEquals(10L, response.getFacultyId());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void updateStatus_Success() {
        when(courseRepository.findById(200L)).thenReturn(Optional.of(sampleCourse));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(programRepository.findById(1L)).thenReturn(Optional.of(sampleProgram));

        CourseDto.Response response = courseService.updateStatus(200L, "INACTIVE");

        assertNotNull(response);
        assertEquals("INACTIVE", response.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }
}