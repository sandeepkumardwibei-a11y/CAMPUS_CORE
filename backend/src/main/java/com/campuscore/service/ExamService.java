package com.campuscore.service;

import com.campuscore.dto.ExamDto;
import com.campuscore.dto.GradeDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.*;
import com.campuscore.entity.Notification.NotificationCategory;
import com.campuscore.exception.ExamException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Safe SLF4J logger import
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j // Plugs the SLF4J logging engine into this class automatically via Lombok
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamScheduleRepository examRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final GradeRecordRepository gradeRepository;
    private final ResultCardRepository resultRepository;
    private final SemesterRegistrationRepository registrationRepository; // 🎯 Added to check student course registration
    private final AttendanceSummaryRepository attendanceSummaryRepository; // 🎯 Added for the 30% attendance eligibility check
    private final FeeInvoiceRepository feeInvoiceRepository; // 🎯 Added to enforce "fees must be paid before appearing for exam"
    private final ApplicationEventPublisher eventPublisher; // 🔔 Injected event publisher for automatic alerts

    /**
     * 🔐 Small helper reused by the faculty-ownership checks below: resolves the
     * currently authenticated user from the security context.
     */
    private User getAuthenticatedUser() {
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new ExamException("Access Denied: Unauthenticated session."));
    }

    /**
     * 🔐 Returns true if the given course has no assigned faculty, or the assigned
     * faculty is someone other than the given user — i.e. the user does NOT own it.
     */
    private boolean isNotAssignedFaculty(Course course, User user) {
        return course.getFaculty() == null || !course.getFaculty().getUserId().equals(user.getUserId());
    }

    @Transactional
    public ExamDto.Response scheduleExam(ExamDto.CreateRequest request) {
        log.info("Entering scheduleExam execution pipeline for courseId: {} and examType: {}",
                request.getCourseId(), request.getExamType());

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", request.getCourseId()));

        if (course.getSemester() != null && !course.getSemester().equals(request.getSemester())) {
            throw new ExamException(
                    "Scheduling Error: The course '" + course.getCourseName() +
                            "' is structured for semester " + course.getSemester() +
                            ", but you are trying to schedule an exam for semester " + request.getSemester() + "."
            );
        }

        // Block scheduling on public/Indian holidays.
        if (HolidayCalendar.isHoliday(request.getExamDate())) {
            throw new ExamException("Scheduling Error: " + request.getExamDate()
                    + " is a holiday (" + HolidayCalendar.nameOf(request.getExamDate())
                    + "). Exams cannot be scheduled on holidays.");
        }

        // Block scheduling that overlaps break times or falls outside college hours (08:00–16:00).
        validateAgainstBreaks(request.getStartTime(),
                request.getDurationMins() != null ? request.getDurationMins() : 180);

        ExamSchedule exam = ExamSchedule.builder()
                .course(course)
                .semester(request.getSemester())
                .academicYear(request.getAcademicYear())
                .examType(ExamSchedule.ExamType.valueOf(request.getExamType().toUpperCase()))
                .examDate(request.getExamDate())
                .startTime(request.getStartTime())
                .durationMins(request.getDurationMins() != null ? request.getDurationMins() : 180)
                .venue(request.getVenue())
                .maxMarks(request.getMaxMarks() != null ? request.getMaxMarks() : BigDecimal.valueOf(100))
                .status(ExamSchedule.ExamStatus.SCHEDULED)
                .build();

        examRepository.save(exam);

        log.info("Successfully scheduled and saved exam record with ID: {}", exam.getExamId());
        return toExamResponse(exam);
    }

    // College day runs 08:00–16:00 with fixed breaks. Exams must fit fully inside a
    // teaching block and must not overlap any break window.
    private static final LocalTime DAY_START  = LocalTime.of(8, 0);
    private static final LocalTime DAY_END    = LocalTime.of(16, 0);
    // { breakStart, breakEnd } windows
    private static final LocalTime[][] BREAKS = {
            { LocalTime.of(10, 0),  LocalTime.of(10, 15) },  // morning short break
            { LocalTime.of(12, 0),  LocalTime.of(13, 0) },   // lunch break
            { LocalTime.of(14, 45), LocalTime.of(15, 0) },   // afternoon short break
    };

    private void validateAgainstBreaks(LocalTime startTime, int durationMins) {
        if (startTime == null) return;
        LocalTime endTime = startTime.plusMinutes(durationMins);

        if (startTime.isBefore(DAY_START) || endTime.isAfter(DAY_END)) {
            throw new ExamException("Scheduling Error: Exams must be within college hours (08:00–16:00).");
        }
        for (LocalTime[] b : BREAKS) {
            LocalTime bs = b[0], be = b[1];
            // overlap if start < breakEnd AND end > breakStart
            if (startTime.isBefore(be) && endTime.isAfter(bs)) {
                throw new ExamException("Scheduling Error: The selected time overlaps a scheduled break ("
                        + bs + "–" + be + "). Please pick another slot.");
            }
        }
    }

    @Transactional(readOnly = true)
    public ExamDto.Response getExamById(Long id) {
        log.info("Fetching exam configuration properties for examId: {}", id);
        ExamSchedule exam = examRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSchedule", "id", id));

        // 🔐 FACULTY OWNERSHIP CHECK: faculty may only view exam details for courses they teach.
        User currentUser = getAuthenticatedUser();
        if (currentUser.getRole() == User.Role.FACULTY && isNotAssignedFaculty(exam.getCourse(), currentUser)) {
            throw new ExamException("Access Denied: You can only view exam details for courses you teach.");
        }

        return toExamResponse(exam);
    }

    @Transactional(readOnly = true)
    public Page<ExamDto.Response> getExamsBySemester(String academicYear, Integer semester, Pageable pageable) {
        log.info("Querying system exam index sheets for academicYear: {} and semester: {}", academicYear, semester);

        // 🔐 FACULTY OWNERSHIP CHECK: faculty only ever see exams for courses they teach.
        User currentUser = getAuthenticatedUser();
        if (currentUser.getRole() == User.Role.FACULTY) {
            return examRepository.findByAcademicYearAndSemesterAndCourseFacultyUserId(
                    academicYear, semester, currentUser.getUserId(), pageable).map(this::toExamResponse);
        }

        return examRepository.findByAcademicYearAndSemester(academicYear, semester, pageable).map(this::toExamResponse);
    }

    @Transactional(readOnly = true)
    public List<ExamDto.Response> getExamsByCourse(Long courseId, String academicYear) {
        log.info("Querying system exam matrices array for courseId: {} and academicYear: {}", courseId, academicYear);

        // 🔐 FACULTY OWNERSHIP CHECK: faculty may only view exams for courses they teach.
        User currentUser = getAuthenticatedUser();
        if (currentUser.getRole() == User.Role.FACULTY) {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
            if (isNotAssignedFaculty(course, currentUser)) {
                throw new ExamException("Access Denied: You can only view exams for courses you teach.");
            }
        }

        return examRepository.findByCourseCourseIdAndAcademicYear(courseId, academicYear).stream()
                .map(this::toExamResponse).collect(Collectors.toList());
    }

    @Transactional
    public void enterGrades(Long examId, List<GradeDto.EnterGradeRequest> grades, Long facultyId) {
        log.info("Entering enterGrades transactional sequence loop for examId: {} submitted by facultyId: {}", examId, facultyId);

        ExamSchedule exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSchedule", "id", examId));

        // RULE: Grades can only be entered once the exam controller has marked the
        // exam as CONDUCTED. Faculty cannot pre-emptively grade a SCHEDULED exam.
        if (exam.getStatus() != ExamSchedule.ExamStatus.CONDUCTED) {
            throw new ExamException(
                    "Grading Error: Grades can only be entered for exams marked as CONDUCTED. Current status: "
                            + exam.getStatus() + ". Ask the exam controller to mark the exam as conducted first."
            );
        }

        User faculty = userRepository.findById(facultyId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", facultyId));

        Course course = exam.getCourse();

        // 🔐 FACULTY OWNERSHIP CHECK: a faculty member may only grade exams for the
        // course assigned to them, and only under their own identity (they cannot
        // submit grades on behalf of a different faculty member).
        User currentUser = getAuthenticatedUser();
        if (currentUser.getRole() == User.Role.FACULTY) {
            if (!currentUser.getUserId().equals(facultyId)) {
                throw new ExamException("Access Denied: You can only submit grades under your own faculty identity.");
            }
            if (isNotAssignedFaculty(course, currentUser)) {
                throw new ExamException("Access Denied: You can only enter grades for the course you teach ('"
                        + course.getCourseName() + "' is not assigned to you).");
            }
        }

        for (GradeDto.EnterGradeRequest req : grades) {
            User student = userRepository.findById(req.getStudentId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.getStudentId()));

            if (student.getRole() != User.Role.STUDENT) {
                throw new ExamException(
                        "Grading Error: The user ID " + req.getStudentId() + " (" + student.getName() +
                                ") is not a student. Only student roles can be assigned grades."
                );
            }

            //  RULE 0: Fees must be PAID for this academic year & semester before the
            //  student is allowed to appear for the exam.
            FeeInvoice invoice = feeInvoiceRepository
                    .findByStudentUserIdAndAcademicYearAndSemester(
                            student.getUserId(), exam.getAcademicYear(), exam.getSemester())
                    .orElseThrow(() -> new ExamException(
                            "Fee Payment Required: No fee invoice was found for student '" + student.getName() +
                                    "' (ID: " + student.getUserId() + ") for " + exam.getAcademicYear() +
                                    ", Semester " + exam.getSemester() + ". The student must clear their fees before appearing for the exam."
                    ));

            if (invoice.getStatus() != FeeInvoice.InvoiceStatus.PAID
                    && invoice.getStatus() != FeeInvoice.InvoiceStatus.WAIVED) {
                throw new ExamException(
                        "Fee Payment Required: Student '" + student.getName() + "' (ID: " + student.getUserId() +
                                ") has not cleared their fees for " + exam.getAcademicYear() + ", Semester " + exam.getSemester() +
                                " (current status: " + invoice.getStatus() + "). Fees must be fully paid before the student can appear for the '" +
                                course.getCourseName() + "' exam."
                );
            }

            //  RULE 1: Verify if the student is registered for this course
            boolean isRegistered = registrationRepository.findByStudentUserId(student.getUserId()).stream()
                    .anyMatch(reg -> reg.getCourses().stream().anyMatch(c -> c.getCourseId().equals(course.getCourseId())));

            if (!isRegistered) {
                throw new ExamException(
                        "Access Blocked: Student '" + student.getName() + "' (ID: " + student.getUserId() +
                                ") is not registered for the course '" + course.getCourseName() +
                                "'. Only registered students are eligible to take this exam."
                );
            }

            //  RULE 2: Verify if the student has at least 30% attendance
            BigDecimal attendancePercent = attendanceSummaryRepository
                    .findByStudentUserIdAndCourseCourseIdAndSemesterAndAcademicYear(
                            student.getUserId(), course.getCourseId(), exam.getSemester(), exam.getAcademicYear())
                    .map(AttendanceSummary::getAttendancePercent)
                    .orElse(BigDecimal.ZERO); // Default to 0% if no attendance records exist yet

            if (attendancePercent.compareTo(BigDecimal.valueOf(30.00)) < 0) {
                throw new ExamException(
                        "Eligibility Failed: Student '" + student.getName() + "' (ID: " + student.getUserId() +
                                ") has an attendance of " + attendancePercent + "%, which is below the mandatory 30% limit " +
                                "required to qualify for the '" + course.getCourseName() + "' exam."
                );
            }

            //  RULE 3: Marks obtained must be within [0, exam.maxMarks] — never negative,
            //  never above the paper's maximum.
            if (req.getMarksObtained() == null) {
                throw new ExamException(
                        "Grading Error: Marks are required for student '" + student.getName() + "' (ID: " + student.getUserId() + ")."
                );
            }
            if (req.getMarksObtained().compareTo(BigDecimal.ZERO) < 0) {
                throw new ExamException(
                        "Grading Error: Marks obtained for student '" + student.getName() + "' (ID: " + student.getUserId() +
                                ") cannot be negative (got " + req.getMarksObtained() + ")."
                );
            }
            if (req.getMarksObtained().compareTo(exam.getMaxMarks()) > 0) {
                throw new ExamException(
                        "Grading Error: Marks obtained for student '" + student.getName() + "' (ID: " + student.getUserId() +
                                ") is " + req.getMarksObtained() + ", which exceeds the maximum marks of " + exam.getMaxMarks() +
                                " for this exam."
                );
            }

            Optional<GradeRecord> existing = gradeRepository.findByExamExamIdAndStudentUserId(examId, student.getUserId());
            GradeRecord gradeRec;
            if (existing.isPresent()) {
                gradeRec = existing.get();
                gradeRec.setMarksObtained(req.getMarksObtained());
                gradeRec.setGrade(calculateGrade(req.getMarksObtained(), exam.getMaxMarks()));
                gradeRec.setSubmittedBy(faculty);
            } else {
                gradeRec = GradeRecord.builder()
                        .exam(exam)
                        .student(student)
                        .marksObtained(req.getMarksObtained())
                        .maxMarks(exam.getMaxMarks())
                        .grade(calculateGrade(req.getMarksObtained(), exam.getMaxMarks()))
                        .submittedBy(faculty)
                        .status(GradeRecord.GradeStatus.DRAFT)
                        .build();
            }
            gradeRepository.save(gradeRec);
        }
        log.info("Successfully completed entering grades collection iteration stack for examId: {}", examId);
    }

    // 🔐 RESTRICTED: Only the exam controller (via the ExamController's role check) may
    // move an exam from SCHEDULED to CONDUCTED. This gates both grade entry and publishing.
    @Transactional
    public ExamDto.Response markExamConducted(Long examId) {
        log.info("Entering markExamConducted state transition for examId: {}", examId);

        ExamSchedule exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSchedule", "id", examId));

        if (exam.getStatus() != ExamSchedule.ExamStatus.SCHEDULED) {
            throw new ExamException(
                    "Status Update Error: Only a SCHEDULED exam can be marked as CONDUCTED. Current status: "
                            + exam.getStatus() + "."
            );
        }

        exam.setStatus(ExamSchedule.ExamStatus.CONDUCTED);
        examRepository.save(exam);

        log.info("Successfully transitioned examId: {} from SCHEDULED to CONDUCTED", examId);
        return toExamResponse(exam);
    }

    // 🔐 RESTRICTED: Only ADMIN or EXAM_CONTROLLER (via the ExamController's role check)
    // may cancel a scheduled exam. Only a SCHEDULED exam can be cancelled — once it has
    // been CONDUCTED (grades may already be entered/published) or already CANCELLED,
    // there's nothing left to cancel.
    @Transactional
    public ExamDto.Response cancelExam(Long examId) {
        log.info("Entering cancelExam state transition for examId: {}", examId);

        ExamSchedule exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSchedule", "id", examId));

        if (exam.getStatus() != ExamSchedule.ExamStatus.SCHEDULED) {
            throw new ExamException(
                    "Status Update Error: Only a SCHEDULED exam can be cancelled. Current status: "
                            + exam.getStatus() + "."
            );
        }

        exam.setStatus(ExamSchedule.ExamStatus.CANCELLED);
        examRepository.save(exam);

        log.info("Successfully transitioned examId: {} from SCHEDULED to CANCELLED", examId);
        return toExamResponse(exam);
    }

    @Transactional
    public void publishGrades(Long examId) {
        log.info("Entering publishGrades state migration sequence for examId: {}", examId);

        ExamSchedule exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSchedule", "id", examId));

        // RULE: Grades can only be published once the exam controller has marked the
        // exam as CONDUCTED (i.e. the exam has actually taken place).
        if (exam.getStatus() != ExamSchedule.ExamStatus.CONDUCTED) {
            throw new ExamException(
                    "Publishing Error: Grades can only be published for exams marked as CONDUCTED. Current status: "
                            + exam.getStatus() + "."
            );
        }

        List<GradeRecord> grades = gradeRepository.findByExamExamId(examId);
        if (grades.isEmpty()) {
            throw new ExamException("Publishing Error: No grades have been entered for this exam yet.");
        }
        if (grades.stream().allMatch(g -> g.getStatus() == GradeRecord.GradeStatus.PUBLISHED)) {
            throw new ExamException("Publishing Error: Grades for this exam have already been published.");
        }

        for (GradeRecord g : grades) {
            g.setStatus(GradeRecord.GradeStatus.PUBLISHED);
            gradeRepository.save(g);

            String gradeAlertMessage = String.format(
                    "Grades Published: Your exam results for '%s' (%s) have been finalized. Grade Secured: %s.",
                    exam.getCourse().getCourseName(),
                    exam.getExamType().name(),
                    g.getGrade()
            );

            // 🔔 AUTOMATIC NOTIFICATION: Dispatch grade updates straight to each individual student profile
            eventPublisher.publishEvent(new NotificationDto.Event(
                    g.getStudent(),
                    gradeAlertMessage,
                    NotificationCategory.EXAM
            ));
        }
        log.info("Successfully committed state transitions to PUBLISHED status for examId: {}", examId);
    }

    // 🎯 SECURED: Enforce that students can only fetch their own grades for their registered courses
    @Transactional(readOnly = true)
    public List<GradeDto.Response> getExamGrades(Long examId) {
        log.info("Verifying security privileges for getExamGrades context request on examId: {}", examId);

        ExamSchedule exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSchedule", "id", examId));

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new ExamException("Access Denied: Unauthenticated session."));

        // If a student is fetching the entire exam's grades, block them immediately
        if (currentUser.getRole() == User.Role.STUDENT) {
            throw new ExamException("Access Denied: Students are not authorized to access global exam sheets.");
        }

        // 🔐 FACULTY OWNERSHIP CHECK: faculty may only view grades for courses they teach.
        if (currentUser.getRole() == User.Role.FACULTY && isNotAssignedFaculty(exam.getCourse(), currentUser)) {
            throw new ExamException("Access Denied: You can only view grades for courses you teach.");
        }

        return gradeRepository.findByExamExamId(examId).stream()
                .map(this::toGradeResponse).collect(Collectors.toList());
    }

    // 🎯 SECURED: Students can only view their own grades and courses
    @Transactional(readOnly = true)
    public List<GradeDto.Response> getStudentGrades(Long studentId) {
        log.info("Verifying security authorization parameters for getStudentGrades access block targeting studentId: {}", studentId);

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new ExamException("Access Denied: Unauthenticated session."));

        // Ensure students can only see their own grades
        if (currentUser.getRole() == User.Role.STUDENT && !currentUser.getUserId().equals(studentId)) {
            throw new ExamException("Access Denied: You cannot request grade sheets belonging to other student accounts.");
        }

        return gradeRepository.findByStudentUserId(studentId).stream()
                .map(this::toGradeResponse).collect(Collectors.toList());
    }

    @Transactional
    public GradeDto.ResultResponse compileResultCard(Long studentId, String academicYear, Integer semester) {
        log.info("Entering compileResultCard calculation index flow for studentId: {} [Term: {}, Sem: {}]",
                studentId, academicYear, semester);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", studentId));

        if (student.getRole() != User.Role.STUDENT) {
            throw new ExamException("Result Error: Cannot compile academic report card because the user ID " + studentId + " is not a student.");
        }

        List<GradeRecord> grades = gradeRepository.findByStudentUserId(studentId).stream()
                .filter(g -> g.getStatus() == GradeRecord.GradeStatus.PUBLISHED)
                .filter(g -> g.getExam().getAcademicYear().equals(academicYear) && g.getExam().getSemester().equals(semester))
                .collect(Collectors.toList());

        if (grades.isEmpty()) {
            throw new ExamException("No published grades found for calculation");
        }

        int totalCredits = 0;
        int weightedPoints = 0;
        int backlogs = 0;

        for (GradeRecord g : grades) {
            Course course = g.getExam().getCourse();
            int credits = course.getCredits();
            int points = getGradePoints(g.getGrade());
            if (points == 0) {
                backlogs++;
            }
            weightedPoints += points * credits;
            totalCredits += credits;
        }

        BigDecimal sgpa = BigDecimal.ZERO;
        if (totalCredits > 0) {
            sgpa = BigDecimal.valueOf(weightedPoints).divide(BigDecimal.valueOf(totalCredits), 2, RoundingMode.HALF_UP);
        }

        List<ResultCard> previousResults = resultRepository.findByStudentUserId(studentId).stream()
                .filter(r -> r.getStatus() == ResultCard.ResultStatus.PUBLISHED)
                .filter(r -> !(r.getAcademicYear().equals(academicYear) && r.getSemester().equals(semester)))
                .collect(Collectors.toList());

        BigDecimal sumSgpa = sgpa;
        int semCount = 1;
        for (ResultCard r : previousResults) {
            sumSgpa = sumSgpa.add(r.getSgpa());
            semCount++;
        }
        BigDecimal cgpa = sumSgpa.divide(BigDecimal.valueOf(semCount), 2, RoundingMode.HALF_UP);

        ResultCard rc = resultRepository.findByStudentUserIdAndAcademicYearAndSemester(studentId, academicYear, semester)
                .orElse(ResultCard.builder()
                        .student(student)
                        .academicYear(academicYear)
                        .semester(semester)
                        .build());

        rc.setSgpa(sgpa);
        rc.setCgpa(cgpa);
        rc.setBacklogs(backlogs);
        rc.setStatus(ResultCard.ResultStatus.PUBLISHED);
        resultRepository.save(rc);

        String resultCardAlertMessage = String.format(
                "Official Marksheet Issued! Your official result card for Semester %d (%s) has been generated. Current SGPA: %s | CGPA: %s. Active Backlogs: %d.",
                rc.getSemester(),
                rc.getAcademicYear(),
                sgpa.toString(),
                cgpa.toString(),
                backlogs
        );

        // 🔔 AUTOMATIC NOTIFICATION: Alert student that their formal term marksheet has been finalized
        eventPublisher.publishEvent(new NotificationDto.Event(
                student,
                resultCardAlertMessage,
                NotificationCategory.EXAM
        ));

        log.info("Successfully compiled result card with dynamic matrix outcomes for studentId: {}", studentId);
        return toResultResponse(rc);
    }

    @Transactional(readOnly = true)
    public boolean isOwner(Long studentId, Authentication authentication) {
        if (authentication == null) return false;
        String username = authentication.getName();
        return userRepository.findByEmail(username)
                .map(user -> user.getUserId().equals(studentId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<GradeDto.ResultResponse> getStudentResults(Long studentId) {
        log.info("Verifying security parameters inside getStudentResults context for studentId: {}", studentId);

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new ExamException("Access Denied: Unauthenticated session."));

        if (currentUser.getRole() == User.Role.STUDENT && !currentUser.getUserId().equals(studentId)) {
            throw new ExamException("Access Denied: You cannot request academic result sheets belonging to other students.");
        }

        return resultRepository.findByStudentUserId(studentId).stream()
                .map(this::toResultResponse).collect(Collectors.toList());
    }

    private String calculateGrade(BigDecimal marks, BigDecimal maxMarks) {
        if (maxMarks == null || maxMarks.compareTo(BigDecimal.ZERO) == 0) return "F";
        BigDecimal percentage = marks.multiply(BigDecimal.valueOf(100)).divide(maxMarks, 2, RoundingMode.HALF_UP);
        if (percentage.compareTo(BigDecimal.valueOf(90)) >= 0) return "A+";
        if (percentage.compareTo(BigDecimal.valueOf(80)) >= 0) return "A";
        if (percentage.compareTo(BigDecimal.valueOf(70)) >= 0) return "B+";
        if (percentage.compareTo(BigDecimal.valueOf(60)) >= 0) return "B";
        if (percentage.compareTo(BigDecimal.valueOf(50)) >= 0) return "C";
        if (percentage.compareTo(BigDecimal.valueOf(40)) >= 0) return "D";
        return "F";
    }

    private int getGradePoints(String grade) {
        if (grade == null) return 0;
        return switch (grade.toUpperCase()) {
            case "A+" -> 10;
            case "A" -> 9;
            case "B+" -> 8;
            case "B" -> 7;
            case "C" -> 6;
            case "D" -> 5;
            default -> 0;
        };
    }

    private ExamDto.Response toExamResponse(ExamSchedule e) {
        return ExamDto.Response.builder()
                .examId(e.getExamId())
                .courseId(e.getCourse().getCourseId())
                .courseName(e.getCourse().getCourseName())
                .courseCode(e.getCourse().getCourseCode())
                .semester(e.getSemester())
                .academicYear(e.getAcademicYear())
                .examType(e.getExamType().name())
                .examDate(e.getExamDate())
                .startTime(e.getStartTime())
                .durationMins(e.getDurationMins())
                .venue(e.getVenue())
                .maxMarks(e.getMaxMarks())
                .status(e.getStatus().name())
                .build();
    }

    private GradeDto.Response toGradeResponse(GradeRecord g) {
        return GradeDto.Response.builder()
                .gradeId(g.getGradeId())
                .examId(g.getExam().getExamId())
                .courseCode(g.getExam().getCourse().getCourseCode())
                .courseName(g.getExam().getCourse().getCourseName())
                .studentId(g.getStudent().getUserId())
                .studentName(g.getStudent().getName())
                .marksObtained(g.getMarksObtained())
                .maxMarks(g.getMaxMarks())
                .grade(g.getGrade())
                .status(g.getStatus().name())
                .build();
    }

    private GradeDto.ResultResponse toResultResponse(ResultCard r) {
        return GradeDto.ResultResponse.builder()
                .resultId(r.getResultId())
                .studentId(r.getStudent().getUserId())
                .studentName(r.getStudent().getName())
                .academicYear(r.getAcademicYear())
                .semester(r.getSemester())
                .sgpa(r.getSgpa())
                .cgpa(r.getCgpa())
                .backlogs(r.getBacklogs())
                .status(r.getStatus().name())
                .build();
    }
}