package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.AttendanceDto;
import com.campuscore.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService; //

    @PostMapping("/mark")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    public ResponseEntity<ApiResponse<Void>> markAttendance(@Valid @RequestBody AttendanceDto.MarkRequest request) {
        attendanceService.markAttendance(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Attendance marked successfully"));
    }

    /**
     * 🔐 STUDENT & STAFF ROUTE: Students are limited strictly to self data metrics checks via expressions
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY', 'EXAM_CONTROLLER') or (hasRole('STUDENT') and #studentId == authentication.principal.userId)")
    public ResponseEntity<ApiResponse<List<AttendanceDto.SummaryResponse>>> getStudentSummaries(
            @PathVariable Long studentId,
            @RequestParam String academicYear) {
        List<AttendanceDto.SummaryResponse> response = attendanceService.getStudentSummaries(studentId, academicYear);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched student attendance summaries successfully."));
    }

    @GetMapping("/course/{courseId}/shortage")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    public ResponseEntity<ApiResponse<List<AttendanceDto.SummaryResponse>>> getShortageListByCourse(@PathVariable Long courseId) {
        List<AttendanceDto.SummaryResponse> response = attendanceService.getShortageListByCourse(courseId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched shortage list for course"));
    }

    /**
     * 🔐 ADMIN ONLY: Admin records faculty logs using name, date, and status fields
     */
    @PostMapping("/faculty/mark")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AttendanceDto.FacultyResponse>> markFacultyAttendance(
            @Valid @RequestBody AttendanceDto.FacultyMarkRequest request) {
        AttendanceDto.FacultyResponse response = attendanceService.markFacultyAttendance(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Faculty attendance logged successfully."));
    }

    /**
     * 🔐 FACULTY & ADMIN ROUTE: Faculty members are isolated to view only their exact personal records
     */
    @GetMapping("/faculty/{facultyId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN') or (hasRole('FACULTY') and #facultyId == authentication.principal.userId)")
    public ResponseEntity<ApiResponse<List<AttendanceDto.FacultyResponse>>> getFacultyAttendance(
            @PathVariable Long facultyId) {
        List<AttendanceDto.FacultyResponse> response = attendanceService.getFacultyAttendance(facultyId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched faculty attendance records successfully."));
    }
}