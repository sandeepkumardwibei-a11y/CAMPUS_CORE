package com.campuscore.service;

import com.campuscore.dto.CourseDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.dto.SemesterRegistrationDto;
import com.campuscore.entity.Course;
import com.campuscore.entity.Notification.NotificationCategory;
import com.campuscore.entity.Program;
import com.campuscore.entity.SemesterRegistration;
import com.campuscore.entity.Timetable;
import com.campuscore.entity.User;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.exception.SemesterRegistrationException;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemesterRegistrationService {

    private final SemesterRegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;
    private final TimetableRepository timetableRepository;
    private final ApplicationEventPublisher eventPublisher; // 🔔 INJECTED FOR EVENT PUBLISHING

    /**
     *  SECURITY GUARD: Ensures a student can only access or modify their own registration records.
     */
    /**
     *  STRICT SELF-REGISTRATION GUARD (registration only):
     *  Unlike verifyDataOwnership, this does NOT allow staff/admin to act on a
     *  student's behalf. The authenticated user must BE the student they are
     *  registering. This keeps registration a student-only, self-only action
     *  even if the endpoint authorities are later widened.
     */
    private void verifyStudentRegisteringForSelf(Long studentUserId) {
        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", authenticatedEmail));

        if (currentUser.getRole() != User.Role.STUDENT) {
            log.warn("Registration blocked: non-student user {} (role {}) attempted to register semester for student ID {}",
                    currentUser.getUserId(), currentUser.getRole(), studentUserId);
            throw new SemesterRegistrationException("Access Denied: Only a student can create a semester registration.");
        }

        if (!currentUser.getUserId().equals(studentUserId)) {
            log.warn("Registration blocked: student {} attempted to register on behalf of another student ID {}",
                    currentUser.getUserId(), studentUserId);
            throw new SemesterRegistrationException("Access Denied: You can only register a semester for your own account.");
        }
    }

    private void verifyDataOwnership(Long studentUserId) {
        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        log.debug("Verifying data ownership context for email: {} against student target ID: {}", authenticatedEmail, studentUserId);
        
        User currentUser = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> {
                    log.error("Security verification fault: Authenticated email identifier context '{}' not found in system storage", authenticatedEmail);
                    return new ResourceNotFoundException("User", "email", authenticatedEmail);
                });

        boolean isStaffOrAdmin = currentUser.getRole() == User.Role.ADMIN || 
                                 currentUser.getRole() == User.Role.EXAM_CONTROLLER ||
                                 currentUser.getRole() == User.Role.FACULTY;

        if (!isStaffOrAdmin && !currentUser.getUserId().equals(studentUserId)) {
            log.warn("Access Denied: Unauthorized security access mismatch. Authenticated user ID {} attempted processing data for student ID {}", currentUser.getUserId(), studentUserId);
            throw new SemesterRegistrationException("Access Denied: You are not authorized to view or modify this student's data.");
        }
    }

    @Transactional
    public SemesterRegistrationDto.Response register(SemesterRegistrationDto.CreateRequest request) {
        log.info("Processing semester registration request initialization parameters for student ID: {}, Program ID: {}, Semester: {}", 
                request.getStudentId(), request.getProgramId(), request.getSemester());

        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> {
                    log.error("Registration terminated: Student record target identifier missing for ID: {}", request.getStudentId());
                    return new ResourceNotFoundException("User", "id", request.getStudentId());
                });

        if (student.getRole() != User.Role.STUDENT) {
            log.warn("Registration rejected: User context mapping with ID {} does not possess STUDENT credentials status flags", request.getStudentId());
            throw new SemesterRegistrationException("Registration Error: Provided User ID does not belong to a student account.");
        }

        //  RULE: Semester must be within the valid 1-8 range.
        if (request.getSemester() == null || request.getSemester() < 1 || request.getSemester() > 8) {
            throw new SemesterRegistrationException(
                    "Registration Error: Semester must be between 1 and 8 (got " + request.getSemester() + ")."
            );
        }

        //  Security Boundary Check — student may only register for themselves
        verifyStudentRegisteringForSelf(request.getStudentId());

        //  PREVENT ANY SECOND ACTIVE REGISTRATION
        //  Rule: once a student has an active (REGISTERED) or confirmed (CONFIRMED) registration
        //  for ANY semester/year, they cannot register again until it is withdrawn.
        List<SemesterRegistration> existingRegs = registrationRepository.findByStudentUserId(request.getStudentId());
        SemesterRegistration activeReg = existingRegs.stream()
                .filter(r -> r.getStatus() == SemesterRegistration.RegistrationStatus.REGISTERED ||
                             r.getStatus() == SemesterRegistration.RegistrationStatus.CONFIRMED)
                .findFirst()
                .orElse(null);

        if (activeReg != null) {
            log.warn("Registration rejected: Student ID {} already has an active registration (ID {}, Semester {}, Year {}, Status {})",
                    request.getStudentId(), activeReg.getRegistrationId(), activeReg.getSemester(),
                    activeReg.getAcademicYear(), activeReg.getStatus());
            throw new SemesterRegistrationException(
                    "Registration Failed: You are already registered for Semester " + activeReg.getSemester() +
                    " (Academic Year " + activeReg.getAcademicYear() + "). A student can only be registered for one semester at a time.");
        }

        if (registrationRepository.findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(
                request.getStudentId(), request.getProgramId(), request.getAcademicYear(), request.getSemester()).isPresent()) {
            log.warn("Registration rejected: Duplicate combination tracking context matches for Student ID: {}, Program ID: {}, Term: {}", request.getStudentId(), request.getProgramId(), request.getSemester());
            throw new SemesterRegistrationException("Student is already registered for this exact semester.");
        }

        Program program = programRepository.findById(request.getProgramId())
                .orElseThrow(() -> {
                    log.error("Registration processing faulted: Target program mapping records absent for ID: {}", request.getProgramId());
                    return new ResourceNotFoundException("Program", "id", request.getProgramId());
                });

        Set<Course> courses = new HashSet<>();
        int totalCredits = 0;

        //  AUTO-FETCH TRACK: If no specific course IDs are entered, fetch all mandatory courses allocated for this program + semester combo
        if (request.getCourseIds() == null || request.getCourseIds().isEmpty()) {
            log.debug("Empty course tracking sequence detected. Executing fallback automatic compilation lookup routines for Program ID: {} and Term: {}", request.getProgramId(), request.getSemester());
            List<Course> defaultSemesterCourses = courseRepository.findAll().stream()
                    .filter(c -> c.getSemester() != null
                            && c.getSemester().equals(request.getSemester())
                            && courseBelongsToProgram(c, request.getProgramId()))
                    .collect(Collectors.toList());

            if (defaultSemesterCourses.isEmpty()) {
                log.error("Routines evaluation fault: No standard defaults located matching constraints for Program ID: {} and Semester: {}", request.getProgramId(), request.getSemester());
                throw new SemesterRegistrationException("Registration Failed: No matching course catalogue records found for Program ID " 
                        + request.getProgramId() + " in Semester " + request.getSemester());
            }
            courses.addAll(defaultSemesterCourses);
        } else {
            // Process user-submitted course paths
            log.debug("Processing validation constraints checking for {} individual course mapping identifiers requested explicitly", request.getCourseIds().size());
            for (Long cId : request.getCourseIds()) {
                Course course = courseRepository.findById(cId)
                        .orElseThrow(() -> {
                            log.error("Routines evaluation fault: Explicit course criteria element ID: {} absent from storage", cId);
                            return new ResourceNotFoundException("Course", "id", cId);
                        });

                // Validate Program Mismatch (course may belong to several programs now)
                if (!courseBelongsToProgram(course, request.getProgramId())) {
                    log.error("Structural mapping collision: Course '{}' (ID: {}) is not linked to Program ID: {} requested for registration",
                            course.getCourseName(), course.getCourseId(), request.getProgramId());
                    throw new SemesterRegistrationException(
                        "Registration Failed: Course '" + course.getCourseName() +
                        "' does not belong to the requested registration program."
                    );
                }

                if (!course.getSemester().equals(request.getSemester())) {
                    log.error("Structural mapping collision: Course '{}' Semester boundary ({}) conflicts with registration configuration semester value ({})", 
                            course.getCourseName(), course.getSemester(), request.getSemester());
                    throw new SemesterRegistrationException(
                        "Registration Failed: Course '" + course.getCourseName() + "' belongs to Semester " + 
                        course.getSemester() + ". It cannot be mixed into a Semester " + request.getSemester() + " registration."
                    );
                }
                courses.add(course);
            }
        }

        // Compute total credits dynamically from the calculated list
        for (Course c : courses) {
            totalCredits += c.getCredits();
        }
        log.debug("Dynamic credit computation absolute total calculated for structural index evaluation: {}", totalCredits);

        SemesterRegistration reg = SemesterRegistration.builder()
                .student(student)
                .program(program)
                .academicYear(request.getAcademicYear())
                .semester(request.getSemester())
                .courses(courses)
                .totalCredits(totalCredits)
                .status(SemesterRegistration.RegistrationStatus.REGISTERED)
                .build();

        registrationRepository.save(reg);
        log.info("Successfully registered semester trace element metadata, record index generated ID: {}", reg.getRegistrationId());

        // 🔔 AUTOMATIC NOTIFICATION: Inform student that their initial course submission is logged under evaluation
        String registrationMessage = String.format(
                "Semester Registration Submitted: Your course selections for Semester %d (Academic Year %s) have been successfully submitted and are pending academic confirmation.",
                reg.getSemester(),
                reg.getAcademicYear()
        );
        // 🔔 Notification is a side-effect: never let it fail the registration itself.
        try {
            eventPublisher.publishEvent(new NotificationDto.Event(student, registrationMessage, NotificationCategory.ACADEMIC));
        } catch (Exception ex) {
            log.error("Registration saved (ID {}) but notification dispatch failed: {}", reg.getRegistrationId(), ex.getMessage());
        }

        return toResponse(reg);
    }

    @Transactional(readOnly = true)
    public SemesterRegistrationDto.Response getById(Long id) {
        log.debug("Fetching individual registration record log trace element details for ID: {}", id);
        SemesterRegistration reg = findOrThrow(id);
        verifyDataOwnership(reg.getStudent().getUserId());
        return toResponse(reg);
    }

    @Transactional(readOnly = true)
    public List<SemesterRegistrationDto.Response> getByStudent(Long studentId) {
        log.debug("Retrieving cumulative operational semester history matching student owner trace index: {}", studentId);
        verifyDataOwnership(studentId);
        return registrationRepository.findByStudentUserId(studentId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SemesterRegistrationDto.Response> getByCourse(Long courseId) {
        log.debug("Querying active semester enrollment records solely filtering by Course ID: {}", courseId);
        
        // Ensure the course exists first
        Course targetCourse = courseRepository.findById(courseId)
                .orElseThrow(() -> {
                    log.error("Course criteria lookup fault: Element ID {} absent from system records storage", courseId);
                    return new ResourceNotFoundException("Course", "id", courseId);
                });

        // Pull explicitly linked registrations directly using the clean course lookup method
        List<SemesterRegistration> matchingRegistrations = registrationRepository.findByCoursesCourseId(courseId);

        return matchingRegistrations.stream()
                .filter(reg -> reg.getCourses().stream()
                        .anyMatch(c -> c.getCourseId().equals(targetCourse.getCourseId())))
                .map(r -> toResponseFilteredByCourse(r, courseId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SemesterRegistrationDto.Response> getAll() {
        log.debug("Retrieving comprehensive collective set of overall semester allocations database parameters data indices");
        return registrationRepository.findAll().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public SemesterRegistrationDto.Response confirmRegistration(Long id) {
        log.info("Transitioning verification flag state indices to CONFIRMED for Registration reference ID: {}", id);
        SemesterRegistration reg = findOrThrow(id);
        reg.setStatus(SemesterRegistration.RegistrationStatus.CONFIRMED);
        SemesterRegistration saved = registrationRepository.save(reg);
        // NOTE: Timetable slots are NO LONGER auto-generated here. The Admin now
        // assigns every timetable slot manually via the Timetable module.

        // 🔔 AUTOMATIC NOTIFICATION: Alert student that their curriculum enrollment is verified and active
        String confirmationMessage = String.format(
                "Semester Registration Confirmed! Your enrollment for Semester %d (Academic Year %s) is now fully processed. Total Registered Credits: %d. Your class timetable will be published by the administration.",
                saved.getSemester(),
                saved.getAcademicYear(),
                saved.getTotalCredits()
        );
        try {
            eventPublisher.publishEvent(new NotificationDto.Event(saved.getStudent(), confirmationMessage, NotificationCategory.ACADEMIC));
        } catch (Exception ex) {
            log.error("Registration confirmed (ID {}) but notification dispatch failed: {}", saved.getRegistrationId(), ex.getMessage());
        }

        return toResponse(saved);
    }

    // A course belongs to a program if it is the (legacy) primary program OR the
    // program appears in the course's many-to-many programIds collection.
    private boolean courseBelongsToProgram(Course c, Long programId) {
        if (programId == null) return false;
        if (c.getProgram() != null && programId.equals(c.getProgram().getProgramId())) return true;
        return c.getProgramIds() != null && c.getProgramIds().contains(programId);
    }

    private SemesterRegistration findOrThrow(Long id) {
        return registrationRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Data lookup context exception: Semester registration instance logs missing matching parameter ID: {}", id);
                    return new ResourceNotFoundException("SemesterRegistration", "id", id);
                });
    }

    private SemesterRegistrationDto.Response toResponse(SemesterRegistration r) {
        final Long regProgramId = r.getProgram() != null ? r.getProgram().getProgramId() : null;
        final String regProgramName = r.getProgram() != null ? r.getProgram().getProgramName() : null;
        List<CourseDto.Response> courseList = r.getCourses().stream().map(c ->
            CourseDto.Response.builder()
                    .courseId(c.getCourseId())
                    .courseName(c.getCourseName())
                    .courseCode(c.getCourseCode())
                    .programIds(regProgramId != null ? java.util.List.of(regProgramId) : java.util.List.of())
                    .programNames(regProgramName != null ? java.util.List.of(regProgramName) : java.util.List.of())
                    .semester(c.getSemester())
                    .credits(c.getCredits())
                    .facultyId(c.getFaculty() != null ? c.getFaculty().getUserId() : null)
                    .facultyName(c.getFaculty() != null ? c.getFaculty().getName() : null)
                    .maxEnrollment(c.getMaxEnrollment())
                    .status(c.getStatus().name())
                    .build()
        ).collect(Collectors.toList());

        return SemesterRegistrationDto.Response.builder()
                .registrationId(r.getRegistrationId())
                .studentId(r.getStudent().getUserId())
                .studentName(r.getStudent().getName())
                .programId(r.getProgram().getProgramId())
                .programName(r.getProgram().getProgramName())
                .academicYear(r.getAcademicYear())
                .semester(r.getSemester())
                .totalCredits(r.getTotalCredits())
                .status(r.getStatus().name())
                .courses(courseList)
                .build();
    }

    private SemesterRegistrationDto.Response toResponseFilteredByCourse(SemesterRegistration r, Long targetCourseId) {
        final Long regProgramId = r.getProgram() != null ? r.getProgram().getProgramId() : null;
        final String regProgramName = r.getProgram() != null ? r.getProgram().getProgramName() : null;
        List<CourseDto.Response> courseList = r.getCourses().stream()
                .filter(c -> c.getCourseId().equals(targetCourseId))
                .map(c -> CourseDto.Response.builder()
                        .courseId(c.getCourseId())
                        .courseName(c.getCourseName())
                        .courseCode(c.getCourseCode())
                        .programIds(regProgramId != null ? java.util.List.of(regProgramId) : java.util.List.of())
                        .programNames(regProgramName != null ? java.util.List.of(regProgramName) : java.util.List.of())
                        .semester(c.getSemester())
                        .credits(c.getCredits())
                        .facultyId(c.getFaculty() != null ? c.getFaculty().getUserId() : null)
                        .facultyName(c.getFaculty() != null ? c.getFaculty().getName() : null)
                        .maxEnrollment(c.getMaxEnrollment())
                        .status(c.getStatus().name())
                        .build()
                ).collect(Collectors.toList());

        return SemesterRegistrationDto.Response.builder()
                .registrationId(r.getRegistrationId())
                .studentId(r.getStudent().getUserId())
                .studentName(r.getStudent().getName())
                .programId(r.getProgram().getProgramId())
                .programName(r.getProgram().getProgramName())
                .academicYear(r.getAcademicYear())
                .semester(r.getSemester())
                .totalCredits(r.getTotalCredits())
                .status(r.getStatus().name())
                .courses(courseList)
                .build();
    }
}