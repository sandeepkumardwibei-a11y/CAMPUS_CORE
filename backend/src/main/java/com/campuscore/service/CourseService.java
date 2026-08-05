package com.campuscore.service;

import com.campuscore.dto.CourseDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.Course;
import com.campuscore.entity.Program;
import com.campuscore.entity.User;
import com.campuscore.entity.Notification.NotificationCategory;
import com.campuscore.exception.CourseException;
import com.campuscore.exception.DuplicateResourceException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.CourseRepository;
import com.campuscore.repository.ProgramRepository;
import com.campuscore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final ProgramRepository programRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CourseDto.Response createCourse(CourseDto.CreateRequest request) {
        log.info("Entering createCourse logic path for courseCode: {} with courseName: {}",
                request.getCourseCode(), request.getCourseName());

        if (courseRepository.findByCourseCode(request.getCourseCode().trim()).isPresent()) {
            throw new DuplicateResourceException("Course code already exists: " + request.getCourseCode());
        }

        // Validate the linked programs (many-to-many). At least one is expected but not forced.
        List<Long> programIds = new ArrayList<>();
        Program primaryProgram = null;
        if (request.getProgramIds() != null) {
            for (Long pid : request.getProgramIds()) {
                Program p = programRepository.findById(pid)
                        .orElseThrow(() -> new ResourceNotFoundException("Program", "id", pid));
                if (primaryProgram == null) primaryProgram = p;
                programIds.add(pid);
            }
        }

        User faculty = null;
        if (request.getFacultyId() != null) {
            faculty = userRepository.findById(request.getFacultyId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getFacultyId()));
            if (faculty.getRole() != User.Role.FACULTY) {
                throw new CourseException("User assigned is not part of the Faculty group.");
            }
        }

        Course course = Course.builder()
                .courseName(request.getCourseName().trim())
                .courseCode(request.getCourseCode().trim())
                .program(primaryProgram)
                .programIds(programIds)
                .semester(request.getSemester())
                .credits(request.getCredits())
                .faculty(faculty)
                .status(Course.CourseStatus.ACTIVE)
                .build();

        Course savedCourse = courseRepository.save(course);

        if (faculty != null) {
            eventPublisher.publishEvent(new NotificationDto.Event(
                faculty,
                String.format("Course Assignment: You have been assigned as the primary faculty for the newly created course '%s' (%s).",
                    savedCourse.getCourseName(), savedCourse.getCourseCode()),
                NotificationCategory.COURSE
            ));
        }

        log.info("Successfully created course record with ID: {} and code: {}", savedCourse.getCourseId(), savedCourse.getCourseCode());
        return toResponse(savedCourse);
    }

    @Transactional(readOnly = true)
    public CourseDto.Response getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<CourseDto.Response> getByProgram(Long programId) {
        log.info("Querying course records for programId: {}", programId);
        // Courses whose primary program matches OR that list the program in the many-to-many set.
        List<Course> byPrimary = courseRepository.findByProgramProgramId(programId, Pageable.unpaged()).getContent();
        List<Course> byMany = courseRepository.findByProgramIdsContaining(programId);
        java.util.LinkedHashMap<Long, Course> merged = new java.util.LinkedHashMap<>();
        for (Course c : byPrimary) merged.put(c.getCourseId(), c);
        for (Course c : byMany) merged.put(c.getCourseId(), c);
        return merged.values().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseDto.Response> getByFaculty(Long facultyId) {
        return courseRepository.findByFacultyUserId(facultyId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseDto.Response> getByProgramAndSemester(Long programId, Integer semester) {
        return getByProgram(programId).stream()
                .filter(c -> semester == null || semester.equals(c.getSemester()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseDto.Response> getAll() {
        return courseRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public CourseDto.Response assignFaculty(Long courseId, Long facultyId) {
        Course course = findOrThrow(courseId);
        User faculty = userRepository.findById(facultyId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", facultyId));
        if (faculty.getRole() != User.Role.FACULTY) {
            throw new CourseException("Selected User account is not configured with Faculty permissions.");
        }
        course.setFaculty(faculty);
        Course updatedCourse = courseRepository.save(course);

        eventPublisher.publishEvent(new NotificationDto.Event(
            faculty,
            String.format("New Course Allocation: You have been assigned to teach '%s' (%s).",
                updatedCourse.getCourseName(), updatedCourse.getCourseCode()),
            NotificationCategory.COURSE
        ));
        return toResponse(updatedCourse);
    }

    @Transactional
    public CourseDto.Response updateStatus(Long courseId, String status) {
        Course course = findOrThrow(courseId);
        course.setStatus(Course.CourseStatus.valueOf(status.toUpperCase()));
        Course updatedCourse = courseRepository.save(course);

        if (updatedCourse.getFaculty() != null) {
            eventPublisher.publishEvent(new NotificationDto.Event(
                updatedCourse.getFaculty(),
                String.format("Course Status Update: The status of your assigned course '%s' has been updated to %s.",
                    updatedCourse.getCourseName(), updatedCourse.getStatus().name()),
                NotificationCategory.COURSE
            ));
        }
        return toResponse(updatedCourse);
    }

    private Course findOrThrow(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
    }

    private CourseDto.Response toResponse(Course c) {
        List<Long> pids = new ArrayList<>();
        if (c.getProgramIds() != null) pids.addAll(c.getProgramIds());
        // Ensure the primary program appears in the list too
        if (c.getProgram() != null && !pids.contains(c.getProgram().getProgramId())) {
            pids.add(0, c.getProgram().getProgramId());
        }
        List<String> pnames = pids.stream()
                .map(pid -> programRepository.findById(pid).map(Program::getProgramName).orElse("Program " + pid))
                .collect(Collectors.toList());

        return CourseDto.Response.builder()
                .courseId(c.getCourseId())
                .courseName(c.getCourseName())
                .courseCode(c.getCourseCode())
                .programIds(pids)
                .programNames(pnames)
                .semester(c.getSemester())
                .credits(c.getCredits())
                .facultyId(c.getFaculty() != null ? c.getFaculty().getUserId() : null)
                .facultyName(c.getFaculty() != null ? c.getFaculty().getName() : null)
                .status(c.getStatus() != null ? c.getStatus().name() : "ACTIVE")
                .build();
    }
}
