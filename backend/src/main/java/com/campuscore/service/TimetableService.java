package com.campuscore.service;

import com.campuscore.dto.NotificationDto;
import com.campuscore.dto.TimetableDto;
import com.campuscore.entity.Course;
import com.campuscore.entity.Notification.NotificationCategory;
import com.campuscore.entity.Program;
import com.campuscore.entity.SemesterRegistration;
import com.campuscore.entity.Timetable;
import com.campuscore.entity.User;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.exception.TimetableException;
import com.campuscore.repository.CourseRepository;
import com.campuscore.repository.ProgramRepository;
import com.campuscore.repository.TimetableRepository;
import com.campuscore.repository.SemesterRegistrationRepository;
import com.campuscore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final SemesterRegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository; // 🎯 Added for Course verification
    private final ProgramRepository programRepository; // 🎯 Added to validate/resolve the slot's program
    private final ApplicationEventPublisher eventPublisher; // 🔔 INJECTED FOR EVENT PUBLISHING

    /**
     * 🔐 INTERNAL SECURITY HELPER: Validates user data isolation access boundaries.
     */
    private User verifyContextAndGetAuthenticatedUser() {
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        log.debug("Verifying internal security session context for email: {}", currentEmail);
        return userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> {
                    log.error("Security boundary failure: Session email reference '{}' could not be resolved in data storage.", currentEmail);
                    return new TimetableException("Access Denied: Invalid security session framework context.");
                });
    }

    /**
     * College day runs 08:00–16:00 with three fixed breaks. Classes must sit
     * fully inside teaching time and must not overlap any break window.
     */
    private static final LocalTime DAY_START = LocalTime.of(8, 0);
    private static final LocalTime DAY_END   = LocalTime.of(16, 0);
    private static final LocalTime[][] BREAKS = {
            { LocalTime.of(10, 0),  LocalTime.of(10, 15) },  // morning short break
            { LocalTime.of(12, 0),  LocalTime.of(13, 0) },   // lunch break
            { LocalTime.of(14, 45), LocalTime.of(15, 0) },   // afternoon short break
    };

    private void validateTeachingWindow(LocalTime start, LocalTime end) {
        if (start.isBefore(DAY_START) || end.isAfter(DAY_END)) {
            throw new TimetableException("Validation Failed: Classes must be scheduled within college hours (08:00–16:00).");
        }
        for (LocalTime[] b : BREAKS) {
            LocalTime bs = b[0], be = b[1];
            if (start.isBefore(be) && end.isAfter(bs)) {
                throw new TimetableException("Validation Failed: The selected time overlaps a scheduled break ("
                        + bs + "–" + be + "). No classes can be scheduled during break times.");
            }
        }
    }

    /**
     * 🛠️ ADMIN ONLY: Creates a new scheduling slot within the system timetable matrix.
     */
    @Transactional
    public TimetableDto.Response createSlot(TimetableDto.CreateRequest request) {
        log.info("Processing requests to construct timetable slot for Course ID: {}, Venue: {}, Term Day: {}",
                request.getCourseId(), request.getVenue(), request.getDayOfWeek());

        // 1. Chronological Check: Ensure start time is strictly before the end time
        if (request.getStartTime().isAfter(request.getEndTime()) || request.getStartTime().equals(request.getEndTime())) {
            log.warn("Timetable creation rejected: Invalid temporal layout boundaries. Start: {}, End: {}", request.getStartTime(), request.getEndTime());
            throw new TimetableException("Validation Failed: Start time (" + request.getStartTime() +
                    ") must be strictly before the end time (" + request.getEndTime() + ").");
        }

        // 1b. College-hours & break-window guard (item 11):
        //     College day is 08:00–16:00 with fixed breaks. No class may fall
        //     outside those hours or overlap a break.
        validateTeachingWindow(request.getStartTime(), request.getEndTime());

        // 1c. Semester must be within the valid 1-8 range (when provided).
        if (request.getSemester() != null && (request.getSemester() < 1 || request.getSemester() > 8)) {
            throw new TimetableException(
                    "Validation Failed: Semester must be between 1 and 8 (got " + request.getSemester() + ")."
            );
        }

        // 2. Check if course exists
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> {
                    log.error("Timetable creation rejected: Course identifier tracking token missing for ID: {}", request.getCourseId());
                    return new ResourceNotFoundException("Course", "id", request.getCourseId());
                });

        // 2b. The admin must pick which program this class session is for (the
        //     dropdown already shown when creating a slot), and the course must
        //     actually be offered under that program.
        if (request.getProgramId() == null) {
            throw new TimetableException("Validation Failed: Please select a program for this timetable slot.");
        }
        Program program = programRepository.findById(request.getProgramId())
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", request.getProgramId()));
        List<Long> courseProgramIds = course.getProgramIds() != null ? course.getProgramIds() : List.of();
        if (!courseProgramIds.contains(request.getProgramId())) {
            throw new TimetableException("Validation Failed: The course '" + course.getCourseName() +
                    "' is not offered under the program '" + program.getProgramName() + "'.");
        }

        // 3. Cross-verify if course is registered/allocated to the requested semester
        if (!course.getSemester().equals(request.getSemester())) {
            log.error("Structural validation mismatch: Course '{}' Semester constraints ({}) do not equal requested target configuration ({})",
                    course.getCourseName(), course.getSemester(), request.getSemester());
            throw new TimetableException("Validation Failed: The course '" + course.getCourseName() +
                    "' belongs to Semester " + course.getSemester() + ". You cannot assign it to a Semester " +
                    request.getSemester() + " timetable schedule slot.");
        }

        // Parse day safely from string
        Timetable.DayOfWeek targetDay;
        try {
            targetDay = Timetable.DayOfWeek.valueOf(request.getDayOfWeek().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Parsing structural failure: Provided textual representation for DayOfWeek is unrecognizable: {}", request.getDayOfWeek());
            throw new TimetableException("Validation Failed: Invalid day format provided.");
        }

        // 4. Precise scheduling-conflict rules (day + overlapping time window):
        //      a) Same ROOM/venue already booked in that window    -> conflict
        //      b) Same FACULTY already teaching anything else in
        //         that window                                     -> conflict
        //      c) Same PROGRAM (the one explicitly selected for this slot)
        //         already has a class in that window               -> conflict,
        //         since students in that program cannot be in two classes at once.
        //         This compares the program picked for THIS slot against the
        //         program picked for each EXISTING slot (Timetable.programId) —
        //         not the full list of programs the course is merely cross-listed
        //         to — so two different courses that both happen to be offered to
        //         several shared programs don't falsely collide when neither is
        //         actually scheduled for that program at this time.
        //    Explicitly allowed even though the time overlaps:
        //      - Different program, different course, different faculty, different
        //        room (fully unrelated classes)
        log.debug("Evaluating precise scheduling-conflict rules against ALL allocations on day: {}", targetDay);
        List<Timetable> sameDaySlots = timetableRepository.findAll().stream()
                .filter(t -> t.getDayOfWeek() == targetDay)
                .collect(Collectors.toList());

        for (Timetable slot : sameDaySlots) {
            boolean overlaps = (request.getStartTime().isBefore(slot.getEndTime()) &&
                    request.getEndTime().isAfter(slot.getStartTime()));
            if (!overlaps) continue;

            Course existingCourse = slot.getCourse();

            // a) Same room conflict.
            boolean sameRoom = request.getVenue() != null && !request.getVenue().isBlank()
                    && request.getVenue().equalsIgnoreCase(slot.getVenue());
            if (sameRoom) {
                log.warn("Scheduling conflict (room overlap): Venue '{}' vs existing slot ID {}", request.getVenue(), slot.getTimetableId());
                throw new TimetableException("Scheduling Conflict: Venue '" + request.getVenue() +
                        "' is already booked for " + existingCourse.getCourseCode() + " on " + targetDay +
                        " between " + slot.getStartTime() + " and " + slot.getEndTime() +
                        ". The same room cannot host two overlapping classes.");
            }

            // b) Same faculty teaching at the same time — even for "the same course",
            //    since that faculty member cannot physically be in two rooms at once.
            boolean sameFaculty = course.getFaculty() != null && existingCourse.getFaculty() != null
                    && course.getFaculty().getUserId().equals(existingCourse.getFaculty().getUserId());
            if (sameFaculty) {
                log.warn("Scheduling conflict (faculty double-booked): Faculty ID {} vs existing slot ID {}",
                        course.getFaculty().getUserId(), slot.getTimetableId());
                throw new TimetableException("Scheduling Conflict: Faculty '" + course.getFaculty().getName() +
                        "' is already teaching " + existingCourse.getCourseCode() + " (Venue: " + slot.getVenue() + ") on " + targetDay +
                        " between " + slot.getStartTime() + " and " + slot.getEndTime() +
                        ". The same faculty cannot teach two overlapping classes at once.");
            }

            // c) Same program conflict — the program explicitly selected for this
            //    slot already has a different class scheduled in this window.
            boolean sameProgram = slot.getProgramId() != null && slot.getProgramId().equals(request.getProgramId());
            if (sameProgram) {
                log.warn("Scheduling conflict (program overlap): Program ID {} vs existing slot ID {}", request.getProgramId(), slot.getTimetableId());
                throw new TimetableException("Scheduling Conflict: The program '" + program.getProgramName() +
                        "' already has a class (" + existingCourse.getCourseCode() + ", Venue: " + slot.getVenue() +
                        ") scheduled on " + targetDay + " between " + slot.getStartTime() + " and " + slot.getEndTime() +
                        ". A program cannot have two overlapping classes.");
            }
        }


        // Build and preserve entity
        Timetable timetable = Timetable.builder()
                .course(course)
                .programId(request.getProgramId())
                .dayOfWeek(targetDay)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .venue(request.getVenue())
                .academicYear(request.getAcademicYear())
                .semester(request.getSemester())
                .build();

        timetableRepository.save(timetable);
        log.info("Successfully established new scheduling record element instance. Resource generated key ID: {}", timetable.getTimetableId());

        // 🔔 AUTOMATIC NOTIFICATION: Find all students currently registered for this course and notify them of the schedule update
        List<SemesterRegistration> enrolledStudents = registrationRepository.findByCoursesCourseId(course.getCourseId());
        log.debug("Found {} registered student configurations mapping to Course ID: {}. Dispatched scheduling alerts.", enrolledStudents.size(), course.getCourseId());

        String notificationMessage = String.format(
                "New Class Schedule Slot Assigned: A new timetable slot has been configured for '%s' (%s). Day: %s, Time: %s - %s at Venue: %s.",
                course.getCourseName(),
                course.getCourseCode(),
                timetable.getDayOfWeek().name(),
                timetable.getStartTime().toString(),
                timetable.getEndTime().toString(),
                timetable.getVenue()
        );

        for (SemesterRegistration registration : enrolledStudents) {
            eventPublisher.publishEvent(new NotificationDto.Event(registration.getStudent(), notificationMessage, NotificationCategory.ACADEMIC));
        }

        return toResponse(timetable);
    }

    @Transactional(readOnly = true)
    public List<TimetableDto.Response> getAllSlots() {
        log.debug("Retrieving cumulative operational global master timetable metrics data set");
        return timetableRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TimetableDto.Response> getSlotsByCourse(Long courseId) {
        log.debug("Initiating target track lookup sequences matching course reference ID: {}", courseId);
        User currentUser = verifyContextAndGetAuthenticatedUser();

        // 🎯 STUDENT ACCESS CHECK: If a student calls this, check if they are registered for this course
        if (currentUser.getRole() == User.Role.STUDENT) {
            log.debug("Context user evaluates as STUDENT. Verifying class enrollment linkage patterns for User ID: {}", currentUser.getUserId());
            boolean isRegistered = registrationRepository.findByStudentUserId(currentUser.getUserId()).stream()
                    .anyMatch(reg -> reg.getCourses().stream().anyMatch(c -> c.getCourseId().equals(courseId)));

            if (!isRegistered) {
                log.warn("Access Denied: Student account context ID {} is not actively registered to inspect parameters for Course ID {}", currentUser.getUserId(), courseId);
                throw new TimetableException("Access Denied: You cannot view the timetable for a course you are not registered in.");
            }
        }

        return timetableRepository.findByCourse_CourseId(courseId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TimetableDto.Response> getStudentSchedule(Long studentId, Long programId, String academicYear, Integer semester) {
        log.debug("Fetching student personal calendar schedule matrix data for Student ID: {}, Program ID: {}, Semester: {}",
                studentId, programId, semester);
        User currentUser = verifyContextAndGetAuthenticatedUser();

        // 🔐 STUDENT ISOLATION BOUNDARY: Students can only view their own schedule
        if (currentUser.getRole() == User.Role.STUDENT && !currentUser.getUserId().equals(studentId)) {
            log.warn("Security policy execution: Student ID {} blocked from intercepting timetable details of target student path ID {}", currentUser.getUserId(), studentId);
            throw new TimetableException("Access Denied: You are not authorized to view another student's timetable agenda.");
        }

        return registrationRepository
                .findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(studentId, programId, academicYear, semester)
                .map(reg -> {
                    log.debug("Registration record trace verified. Expanding course child segments collection mapping matrix streams.");
                    return reg.getCourses().stream()
                            .flatMap(course -> timetableRepository.findByCourse_CourseId(course.getCourseId()).stream())
                            .filter(t -> t.getAcademicYear().equalsIgnoreCase(academicYear) && t.getSemester().equals(semester))
                            .map(this::toResponse)
                            .collect(Collectors.toList());
                }).orElseThrow(() -> {
                    log.error("Data tracking failure: No verified active registration matching parameters was found for Student ID: {}", studentId);
                    return new TimetableException(
                            "Active registration records not found. Please verify your Student ID, Program ID, Academic Year, and Semester credentials correctly."
                    );
                });
    }

    /**
     * 🎯 STUDENT SELF-SERVICE: Returns the logged-in student's own weekly schedule
     * without requiring them to type their Student/Program ID, academic year or
     * semester — those are inferred from their own (most recent, non-withdrawn)
     * semester registration.
     */
    @Transactional(readOnly = true)
    public List<TimetableDto.Response> getMyStudentSchedule() {
        User currentUser = verifyContextAndGetAuthenticatedUser();
        if (currentUser.getRole() != User.Role.STUDENT) {
            throw new TimetableException("Access Denied: Only students can view their own schedule via this endpoint.");
        }

        SemesterRegistration current = registrationRepository.findByStudentUserId(currentUser.getUserId()).stream()
                .filter(r -> r.getStatus() != SemesterRegistration.RegistrationStatus.WITHDRAWN)
                .max(Comparator.comparing(SemesterRegistration::getAcademicYear)
                        .thenComparing(SemesterRegistration::getSemester))
                .orElseThrow(() -> new TimetableException(
                        "No active semester registration found for your account. Please contact administration."));

        return current.getCourses().stream()
                .flatMap(course -> timetableRepository.findByCourse_CourseId(course.getCourseId()).stream())
                .filter(t -> t.getAcademicYear().equalsIgnoreCase(current.getAcademicYear()) && t.getSemester().equals(current.getSemester()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 🎯 FACULTY SELF-SERVICE: Returns the logged-in faculty member's own teaching
     * timetable — every scheduled slot across every course they are assigned to —
     * without requiring them to look up and type a Course ID.
     */
    @Transactional(readOnly = true)
    public List<TimetableDto.Response> getMyTeachingSchedule() {
        User currentUser = verifyContextAndGetAuthenticatedUser();
        if (currentUser.getRole() != User.Role.FACULTY) {
            throw new TimetableException("Access Denied: Only faculty can view their own teaching schedule via this endpoint.");
        }

        List<Course> myCourses = courseRepository.findByFacultyUserId(currentUser.getUserId());
        return myCourses.stream()
                .flatMap(course -> timetableRepository.findByCourse_CourseId(course.getCourseId()).stream())
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private TimetableDto.Response toResponse(Timetable t) {
        String programName = null;
        if (t.getProgramId() != null) {
            programName = programRepository.findById(t.getProgramId())
                    .map(Program::getProgramName)
                    .orElse(null);
        }
        return TimetableDto.Response.builder()
                .timetableId(t.getTimetableId())
                .courseId(t.getCourse().getCourseId())
                .courseCode(t.getCourse().getCourseCode())
                .courseName(t.getCourse().getCourseName())
                .programId(t.getProgramId())
                .programName(programName)
                .dayOfWeek(t.getDayOfWeek().name())
                .startTime(t.getStartTime())
                .endTime(t.getEndTime())
                .venue(t.getVenue())
                .academicYear(t.getAcademicYear())
                .semester(t.getSemester())
                .build();
    }
}