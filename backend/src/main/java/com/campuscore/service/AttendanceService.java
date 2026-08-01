package com.campuscore.service;

import com.campuscore.dto.AttendanceDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.*;
import com.campuscore.entity.Notification.NotificationCategory;
import com.campuscore.exception.AttendanceException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Safe SLF4J logger import
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j // Plugs the SLF4J logging framework into this service
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository recordRepository;
    private final AttendanceSummaryRepository summaryRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final SemesterRegistrationRepository registrationRepository;
    private final FacultyAttendanceRepository facultyAttendanceRepository;
    private final ApplicationEventPublisher eventPublisher; // 🔔 Injected event publisher for automatic alerts

    /**
     * Helper logic to resolve user security boundaries inside execution loops
     */
    private User getAuthenticatedUser() {
        if (SecurityContextHolder.getContext().getAuthentication() == null ||
                !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            throw new AttendanceException("Access Denied: Unauthenticated session context framework.");
        }
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new AttendanceException("Access Denied: Logged-in credentials match no existing active accounts."));
    }

    /**
     * Attendance (student or faculty) may only be marked for a date that is today
     * or earlier, and never on a Sunday or a gazetted holiday — there are no
     * lectures/working days on those dates.
     */
    private void validateAttendanceDate(java.time.LocalDate date) {
        if (date == null) {
            throw new AttendanceException("Validation Error: Attendance date must be provided.");
        }
        if (date.isAfter(java.time.LocalDate.now())) {
            throw new AttendanceException("Validation Error: " + date
                    + " is a future date. Attendance can only be marked for today or an earlier date.");
        }
        if (date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            throw new AttendanceException("Validation Error: " + date
                    + " is a Sunday. Attendance cannot be marked on Sundays.");
        }
        if (HolidayCalendar.isHoliday(date)) {
            throw new AttendanceException("Validation Error: " + date
                    + " is a holiday (" + HolidayCalendar.nameOf(date)
                    + "). Attendance cannot be marked on holidays.");
        }
    }

    @Transactional
    public void markAttendance(AttendanceDto.MarkRequest request) {
        // Safe entry point logging
        log.info("Entering markAttendance execution flow for courseId: {} on date: {}", 
                request.getCourseId(), request.getLectureDate());

        // 1. Validate Course presence explicitly
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new AttendanceException("Invalid Course Credentials: The provided Course ID (" + request.getCourseId() + ") does not exist in our records."));

        // Block marking attendance on future dates, Sundays, and public/Indian holidays.
        validateAttendanceDate(request.getLectureDate());

        User currentUser = getAuthenticatedUser();
        boolean isAssignedFaculty = course.getFaculty() != null && course.getFaculty().getUserId().equals(currentUser.getUserId());
        boolean isAdmin = currentUser.getRole() == User.Role.ADMIN;

        if (!isAssignedFaculty && !isAdmin) {
            throw new AttendanceException("Access Denied: You do not have permission to log attendance for this course. Only the assigned faculty or an admin can perform this operation.");
        }

        if (request.getRecords() == null || request.getRecords().isEmpty()) {
            throw new AttendanceException("Validation Error: The request payload records list cannot be empty.");
        }

        // 2. Structural item checks inside loop mappings
        for (AttendanceDto.StudentAttendance recordDto : request.getRecords()) {
            if (recordDto.getStudentId() == null) {
                throw new AttendanceException("Validation Error: Found null value inside student ID request list entry.");
            }

            User student = userRepository.findById(recordDto.getStudentId())
                    .orElseThrow(() -> new AttendanceException("Invalid Student Credentials: User profile with ID (" + recordDto.getStudentId() + ") does not exist."));

            // 🎯 INTERCEPT ACCIDENTAL FACULTY OR NON-STUDENT ID ASSIGNMENTS
            if (student.getRole() == User.Role.FACULTY) {
                throw new AttendanceException("Validation Error: User ID (" + recordDto.getStudentId() + ") belongs to a Faculty member ('" + student.getName() + "'). You cannot register student attendance records using a Faculty ID.");
            }

            if (student.getRole() != User.Role.STUDENT) {
                throw new AttendanceException("Validation Error: User ID (" + recordDto.getStudentId() + ") belongs to a " + student.getRole() + ". Attendance entries can only be filed against standard Student account types.");
            }

            // Ensure status parsing is valid before saving
            AttendanceRecord.AttendanceStatus status;
            try {
                status = AttendanceRecord.AttendanceStatus.valueOf(recordDto.getStatus().toUpperCase());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new AttendanceException("Validation Error: Invalid attendance status value '" + recordDto.getStatus() + "' for student '" + student.getName() + "'. Accepted: PRESENT, ABSENT, LATE, OFFICIAL_DUTY.");
            }

            Optional<AttendanceRecord> existingRecordOpt = recordRepository
                    .findByStudentUserIdAndCourseCourseIdAndLectureDate(student.getUserId(), course.getCourseId(), request.getLectureDate());

            AttendanceRecord record = existingRecordOpt.orElseGet(() -> AttendanceRecord.builder()
                    .student(student)
                    .course(course)
                    .lectureDate(request.getLectureDate())
                    .build());

            record.setStatus(status);
            recordRepository.save(record);

            recalculateSummary(student.getUserId(), course.getCourseId());

            // 🔔 AUTOMATIC NOTIFICATION: Alert Student of Attendance Entry
            eventPublisher.publishEvent(new NotificationDto.Event(
                student,
                String.format("Attendance Alert: You have been marked %s for the course %s on %s.", 
                    status.name(), course.getCourseName(), request.getLectureDate().toString()),
                NotificationCategory.ATTENDANCE
            ));
        }

        // Safe completion logging
        log.info("Successfully processed and saved attendance records batch for courseId: {}", request.getCourseId());
    }

    private void recalculateSummary(Long studentId, Long courseId) {
        log.debug("Recalculating attendance summary details for studentId: {} and courseId: {}", studentId, courseId);

        List<AttendanceRecord> records = recordRepository.findByStudentUserIdAndCourseCourseId(studentId, courseId);
        int totalLectures = records.size();
        int attendedLectures = (int) records.stream()
                .filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT ||
                        r.getStatus() == AttendanceRecord.AttendanceStatus.LATE ||
                        r.getStatus() == AttendanceRecord.AttendanceStatus.OFFICIAL_DUTY)
                .count();

        // FIXED: Restored your exact working calculation logic
        BigDecimal percentage = totalLectures > 0
                ? BigDecimal.valueOf(attendedLectures).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalLectures), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Catch unregistered students cleanly to prevent unintended crashes
        SemesterRegistration reg = registrationRepository.findByStudentUserId(studentId).stream()
                .filter(r -> r.getCourses().stream().anyMatch(c -> c.getCourseId().equals(courseId)))
                .findFirst()
                .orElseThrow(() -> new AttendanceException("Data Inconsistency: Student with ID (" + studentId + ") is not officially registered for Course ID: " + courseId));

        AttendanceSummary summary = summaryRepository
                .findByStudentUserIdAndCourseCourseIdAndSemesterAndAcademicYear(studentId, courseId, reg.getSemester(), reg.getAcademicYear())
                .orElse(AttendanceSummary.builder().student(reg.getStudent()).course(courseRepository.getReferenceById(courseId))
                        .semester(reg.getSemester()).academicYear(reg.getAcademicYear()).build());

        summary.setTotalLectures(totalLectures);
        summary.setAttendedLectures(attendedLectures);
        summary.setAttendancePercent(percentage);
        summary.setShortageFlag(percentage.compareTo(BigDecimal.valueOf(75.0)) < 0);
        summaryRepository.save(summary);
    }

    @Transactional
    public AttendanceDto.FacultyResponse markFacultyAttendance(AttendanceDto.FacultyMarkRequest request) {
        // Safe entry point logging
        log.info("Entering markFacultyAttendance execution flow for facultyName: {} on date: {}", 
                request.getFacultyName(), request.getDate());

        if (request.getFacultyName() == null || request.getFacultyName().isBlank()) {
            throw new AttendanceException("Validation Failed: Faculty name field must not be blank.");
        }

        // Block marking faculty attendance on future dates, Sundays, and public/Indian holidays.
        validateAttendanceDate(request.getDate());

        List<User> usersFound = userRepository.findAll().stream()
                .filter(u -> u.getName().equalsIgnoreCase(request.getFacultyName().trim()) && u.getRole() == User.Role.FACULTY)
                .collect(Collectors.toList());

        if (usersFound.isEmpty()) {
            throw new AttendanceException("Validation Failed: Faculty member with name '" + request.getFacultyName() + "' was not found in the university records database.");
        }

        User faculty = usersFound.get(0);

        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new AttendanceException("Validation Failed: Faculty attendance status must be provided.");
        }

        String targetStatus = request.getStatus().trim().toUpperCase();
        if (!targetStatus.equals("PRESENT") && !targetStatus.equals("ABSENT")) {
            throw new AttendanceException("Validation Failed: Invalid status structure '" + request.getStatus() + "' for Faculty. Allowed values are: PRESENT, ABSENT.");
        }

        FacultyAttendanceRecord record = FacultyAttendanceRecord.builder()
                .faculty(faculty)
                .date(request.getDate())
                .status(targetStatus)
                .build();

        facultyAttendanceRepository.save(record);
        
        // 🔔 AUTOMATIC NOTIFICATION: Alert Faculty Member of Admin Update
        eventPublisher.publishEvent(new NotificationDto.Event(
            faculty,
            String.format("Faculty Attendance Update: Your attendance status for %s has been registered as %s.", 
                request.getDate().toString(), targetStatus),
            NotificationCategory.ATTENDANCE
        ));

        // Safe completion logging
        log.info("Successfully marked faculty attendance for facultyUserId: {}", faculty.getUserId());
        return toFacultyResponse(record);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto.SummaryResponse> getStudentSummaries(Long studentId, String academicYear) {
        // Safe entry point logging
        log.info("Entering getStudentSummaries query tracking process for studentId: {} and academicYear: {}", studentId, academicYear);

        if (studentId == null || academicYear == null || academicYear.isBlank()) {
            throw new AttendanceException("Validation Error: Student ID and academic year details must be explicitly supplied.");
        }

        User executingUser = getAuthenticatedUser();

        // 🔐 STUDENT ISOLATION BOUNDARY Check
        if (executingUser.getRole() == User.Role.STUDENT && !executingUser.getUserId().equals(studentId)) {
            throw new AttendanceException("Access Denied: You cannot view attendance logs belonging to other students.");
        }

        User targetStudent = userRepository.findById(studentId)
                .orElseThrow(() -> new AttendanceException("Data Fetching Failure: The student profile ID (" + studentId + ") does not exist."));
        if (targetStudent.getRole() != User.Role.STUDENT) {
            throw new AttendanceException("Validation Error: Selected target profile ID (" + studentId + ") matches a " + targetStudent.getRole() + ", not a student.");
        }

        List<AttendanceSummary> summaries = summaryRepository.findByStudentUserIdAndAcademicYear(studentId, academicYear.trim());
        if (summaries.isEmpty()) {
            throw new AttendanceException("Data Fetching Failure: No attendance summaries found for student '" + targetStudent.getName() + "' during the academic year '" + academicYear + "'.");
        }
        
        // Safe completion logging
        log.info("Successfully retrieved {} attendance summary records for studentId: {}", summaries.size(), studentId);
        return summaries.stream().map(this::toSummaryResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto.FacultyResponse> getFacultyAttendance(Long facultyId) {
        // Safe entry point logging
        log.info("Entering getFacultyAttendance query tracking process for facultyId: {}", facultyId);

        if (facultyId == null) {
            throw new AttendanceException("Validation Error: Faculty ID query param must not be empty.");
        }

        User executingUser = getAuthenticatedUser();

        // 🔐 FACULTY ISOLATION BOUNDARY Check
        if (executingUser.getRole() == User.Role.FACULTY && !executingUser.getUserId().equals(facultyId)) {
            throw new AttendanceException("Access Denied: You cannot view attendance logs belonging to another faculty member.");
        }

        User targetFaculty = userRepository.findById(facultyId)
                .orElseThrow(() -> new AttendanceException("Data Fetching Failure: The faculty profile ID (" + facultyId + ") does not exist."));
        if (targetFaculty.getRole() != User.Role.FACULTY) {
            throw new AttendanceException("Validation Error: Request profile ID (" + facultyId + ") does not map to a university faculty account.");
        }

        List<FacultyAttendanceRecord> records = facultyAttendanceRepository.findByFacultyUserId(facultyId);
        
        // Safe completion logging
        log.info("Successfully fetched {} historical attendance rows for facultyId: {}", records.size(), facultyId);
        return records.stream()
                .map(this::toFacultyResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto.SummaryResponse> getShortageListByCourse(Long courseId) {
        // Safe entry point logging
        log.info("Entering getShortageListByCourse tracking verification index for courseId: {}", courseId);

        if (!courseRepository.existsById(courseId)) {
            throw new AttendanceException("Data Fetching Failure: The requested Course ID (" + courseId + ") does not exist.");
        }
        
        List<AttendanceSummary> shortageList = summaryRepository.findByCourseCourseIdAndShortageFlagTrue(courseId);
        
        // Safe completion logging
        log.info("Successfully generated shortage list metrics index containing {} students for courseId: {}", shortageList.size(), courseId);
        return shortageList.stream()
                .map(this::toSummaryResponse).collect(Collectors.toList());
    }

    private AttendanceDto.SummaryResponse toSummaryResponse(AttendanceSummary s) {
        return AttendanceDto.SummaryResponse.builder()
                .summaryId(s.getSummaryId())
                .studentId(s.getStudent().getUserId())
                .studentName(s.getStudent().getName())
                .courseId(s.getCourse().getCourseId())
                .courseName(s.getCourse().getCourseName())
                .semester(s.getSemester())
                .academicYear(s.getAcademicYear())
                .totalLectures(s.getTotalLectures())
                .attendedLectures(s.getAttendedLectures())
                .attendancePercent(s.getAttendancePercent())
                .shortageFlag(s.getShortageFlag())
                .build();
    }

    private AttendanceDto.FacultyResponse toFacultyResponse(FacultyAttendanceRecord f) {
        return AttendanceDto.FacultyResponse.builder()
                .id(f.getId())
                .facultyId(f.getFaculty().getUserId())
                .facultyName(f.getFaculty().getName())
                .date(f.getDate())
                .status(f.getStatus())
                .build();
    }
}