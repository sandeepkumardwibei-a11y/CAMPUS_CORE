package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.TimetableDto;
import com.campuscore.service.TimetableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@RestController
@RequestMapping("/timetable")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TimetableController {

    private final TimetableService timetableService;

    /**
     * 🔐 ADMIN ONLY: Creates a new scheduling slot within the system timetable matrix.
     * Triggers strict business validations inside the service layer for course existence,
     * matching semester, and schedule overlaps.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TimetableDto.Response>> createSlot(
            @RequestBody TimetableDto.CreateRequest request) {
        // Log trace at request entry point
        log.info("Processing createSlot endpoint request");

        TimetableDto.Response response = timetableService.createSlot(request);
        
        // Log trace at successful response point
        log.info("Successfully processed createSlot endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Timetable slot created successfully."));
    }

    // 🔐 Restricted strictly to Admin and Faculty
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    public ResponseEntity<ApiResponse<List<TimetableDto.Response>>> getAll() {
        // Log trace at request entry point
        log.info("Processing getAll endpoint request");

        List<TimetableDto.Response> response = timetableService.getAllSlots();
        
        // Log trace at successful response point
        log.info("Successfully processed getAll endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched entire university timetable master record."));
    }

    // 🔐 Open to Admin and Faculty, or custom validation for logged-in Students via the Service layer
    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<TimetableDto.Response>>> getByCourse(@PathVariable Long courseId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getByCourse endpoint request for courseId: {}", courseId);

        List<TimetableDto.Response> response = timetableService.getSlotsByCourse(courseId);
        
        // Log trace at successful response point
        log.info("Successfully processed getByCourse endpoint request for courseId: {}", courseId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched structured timetable items per targeted course track."));
    }

    /**
     * 📊 JSON VIEW: Returns the student's schedule as a structured JSON list (matching the
     * response shape of every other Timetable endpoint) so the frontend can render it.
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<TimetableDto.Response>>> getStudentSchedule(
            @PathVariable Long studentId,
            @RequestParam Long programId,
            @RequestParam String academicYear,
            @RequestParam Integer semester) {
        // Log trace at request entry point using safe parameter variables
        log.info("Processing getStudentSchedule endpoint request for studentId: {}, programId: {}, academicYear: {}, semester: {}", 
                studentId, programId, academicYear, semester);

        List<TimetableDto.Response> slots = timetableService.getStudentSchedule(studentId, programId, academicYear, semester);

        // Log trace at successful response point
        log.info("Successfully processed getStudentSchedule endpoint request for studentId: {}", studentId);
        return ResponseEntity.ok(ApiResponse.success(slots, "Fetched student timetable schedule."));
    }

    /**
     * 🎯 STUDENT SELF-SERVICE: The logged-in student's own weekly schedule, with
     * program/academic year/semester auto-detected from their registration —
     * no need to type Student ID, Program ID, year or semester manually.
     */
    @GetMapping("/my-schedule")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<TimetableDto.Response>>> getMySchedule() {
        log.info("Processing getMySchedule endpoint request");

        List<TimetableDto.Response> slots = timetableService.getMyStudentSchedule();

        log.info("Successfully processed getMySchedule endpoint request");
        return ResponseEntity.ok(ApiResponse.success(slots, "Fetched your timetable schedule."));
    }

    /**
     * 🎯 FACULTY SELF-SERVICE: The logged-in faculty member's own teaching
     * timetable, auto-detected from the courses assigned to them.
     */
    @GetMapping("/my-teaching")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<List<TimetableDto.Response>>> getMyTeaching() {
        log.info("Processing getMyTeaching endpoint request");

        List<TimetableDto.Response> slots = timetableService.getMyTeachingSchedule();

        log.info("Successfully processed getMyTeaching endpoint request");
        return ResponseEntity.ok(ApiResponse.success(slots, "Fetched your teaching timetable."));
    }
}