package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.ExamDto;
import com.campuscore.dto.GradeDto;
import com.campuscore.dto.SemesterRegistrationDto;
import com.campuscore.service.ExamService;
import com.campuscore.service.SemesterRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@RestController
@RequestMapping("/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final SemesterRegistrationService registrationService;

    @PostMapping("/schedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_CONTROLLER')")
    public ResponseEntity<ApiResponse<ExamDto.Response>> scheduleExam(@Valid @RequestBody ExamDto.CreateRequest request) {
        // Log trace at request entry point
        log.info("Processing scheduleExam endpoint request");
        
        ExamDto.Response response = examService.scheduleExam(request);
        
        // Log trace at successful response point
        log.info("Successfully processed scheduleExam endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Exam scheduled successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_CONTROLLER', 'FACULTY', 'STUDENT')")
    public ResponseEntity<ApiResponse<ExamDto.Response>> getExamById(@PathVariable Long id) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getExamById endpoint request for examId: {}", id);
        
        ExamDto.Response response = examService.getExamById(id);
        
        // Log trace at successful response point
        log.info("Successfully processed getExamById endpoint request for examId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched exam details"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_CONTROLLER', 'FACULTY', 'STUDENT')")
    public ResponseEntity<ApiResponse<Page<ExamDto.Response>>> getExams(
            @RequestParam String academicYear,
            @RequestParam Integer semester,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Log trace at request entry point using safe string/primitive fields
        log.info("Processing getExams endpoint request for academicYear: {} and semester: {}", academicYear, semester);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<ExamDto.Response> response = examService.getExamsBySemester(academicYear, semester, pageable);
        
        // Log trace at successful response point
        log.info("Successfully processed getExams endpoint request for academicYear: {}", academicYear);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched exams for the semester"));
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_CONTROLLER', 'FACULTY', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<ExamDto.Response>>> getExamsByCourse(
            @PathVariable Long courseId,
            @RequestParam String academicYear) {
        // Log trace at request entry point using safe variables
        log.info("Processing getExamsByCourse endpoint request for courseId: {} and academicYear: {}", courseId, academicYear);
        
        List<ExamDto.Response> response = examService.getExamsByCourse(courseId, academicYear);
        
        // Log trace at successful response point
        log.info("Successfully processed getExamsByCourse endpoint request for courseId: {}", courseId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched exams by course"));
    }

    @PostMapping("/{examId}/grades")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    public ResponseEntity<ApiResponse<Void>> enterGrades(
            @PathVariable Long examId,
            @RequestBody List<GradeDto.EnterGradeRequest> request,
            @RequestParam Long facultyId) {
        // Log trace at request entry point using safe identifiers
        log.info("Processing enterGrades endpoint request for examId: {} by facultyId: {}", examId, facultyId);
        
        examService.enterGrades(examId, request, facultyId);
        
        // Log trace at successful response point
        log.info("Successfully processed enterGrades endpoint request for examId: {}", examId);
        return ResponseEntity.ok(ApiResponse.success(null, "Grades entered successfully"));
    }

    @PutMapping("/{examId}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_CONTROLLER')")
    public ResponseEntity<ApiResponse<Void>> publishGrades(@PathVariable Long examId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing publishGrades endpoint request for examId: {}", examId);
        
        examService.publishGrades(examId);
        
        // Log trace at successful response point
        log.info("Successfully processed publishGrades endpoint request for examId: {}", examId);
        return ResponseEntity.ok(ApiResponse.success(null, "Grades published successfully"));
    }

    // 🔐 SECURED: Students cannot fetch raw class grades
    @GetMapping("/{examId}/grades")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_CONTROLLER', 'FACULTY')")
    public ResponseEntity<ApiResponse<List<GradeDto.Response>>> getExamGrades(@PathVariable Long examId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getExamGrades endpoint request for examId: {}", examId);
        
        List<GradeDto.Response> response = examService.getExamGrades(examId);
        
        // Log trace at successful response point
        log.info("Successfully processed getExamGrades endpoint request for examId: {}", examId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched grades for the exam"));
    }

    // 🔐 SECURED: Only administrators, controllers, or the specific student owner can view these grades
    @GetMapping("/student/{studentId}/grades")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_CONTROLLER') or @examService.isOwner(#studentId, authentication)")
    public ResponseEntity<ApiResponse<List<GradeDto.Response>>> getStudentGrades(@PathVariable Long studentId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getStudentGrades endpoint request for studentId: {}", studentId);
        
        List<GradeDto.Response> response = examService.getStudentGrades(studentId);
        
        // Log trace at successful response point
        log.info("Successfully processed getStudentGrades endpoint request for studentId: {}", studentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched grades for the student"));
    }

    @PostMapping("/student/{studentId}/compile-result")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_CONTROLLER')")
    public ResponseEntity<ApiResponse<GradeDto.ResultResponse>> compileResultCard(
            @PathVariable Long studentId,
            @RequestParam String academicYear,
            @RequestParam Integer semester) {
        // Log trace at request entry point using safe parameters
        log.info("Processing compileResultCard endpoint request for studentId: {}, academicYear: {}, and semester: {}", studentId, academicYear, semester);
        
        GradeDto.ResultResponse response = examService.compileResultCard(studentId, academicYear, semester);
        
        // Log trace at successful response point
        log.info("Successfully processed compileResultCard endpoint request for studentId: {}", studentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Result card compiled successfully"));
    }

    @PutMapping("/registrations/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_CONTROLLER')")
    public ResponseEntity<ApiResponse<SemesterRegistrationDto.Response>> confirmRegistration(@PathVariable Long id) {
        // Log trace at request entry point using safe path variable
        log.info("Processing confirmRegistration endpoint request for registrationId: {}", id);
        
        SemesterRegistrationDto.Response response = registrationService.confirmRegistration(id);
        
        // Log trace at successful response point
        log.info("Successfully processed confirmRegistration endpoint request for registrationId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Registration confirmed successfully"));
    }

    // 🔐 SECURED: Only administrators, controllers, or the specific student owner can view these results
    @GetMapping("/student/{studentId}/results")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_CONTROLLER') or @examService.isOwner(#studentId, authentication)")
    public ResponseEntity<ApiResponse<List<GradeDto.ResultResponse>>> getStudentResults(@PathVariable Long studentId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getStudentResults endpoint request for studentId: {}", studentId);
        
        List<GradeDto.ResultResponse> response = examService.getStudentResults(studentId);
        
        // Log trace at successful response point
        log.info("Successfully processed getStudentResults endpoint request for studentId: {}", studentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched results for the student"));
    }
}