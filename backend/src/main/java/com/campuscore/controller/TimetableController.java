package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.TimetableDto;
import com.campuscore.service.TimetableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import
import org.springframework.http.MediaType;
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
     * 📊 TEXT-PLAIN TABULAR VIEW: Generates an exact plain text ASCII grid table directly inside the response body.
     */
    @GetMapping(value = "/student/{studentId}", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY', 'STUDENT')")
    public ResponseEntity<String> getStudentSchedule(
            @PathVariable Long studentId,
            @RequestParam Long programId,
            @RequestParam String academicYear,
            @RequestParam Integer semester) {
        // Log trace at request entry point using safe parameter variables
        log.info("Processing getStudentSchedule endpoint request for studentId: {}, programId: {}, academicYear: {}, semester: {}", 
                studentId, programId, academicYear, semester);

        List<TimetableDto.Response> slots = timetableService.getStudentSchedule(studentId, programId, academicYear, semester);

        // 1. Build the formal table string header layout grid frame
        StringBuilder table = new StringBuilder();
        table.append(String.format("%-12s | %-12s | %-25s | %-10s | %-10s | %-10s | %-8s\n",
                "TIMETABLE ID", "COURSE CODE", "COURSE NAME", "DAY", "START", "END", "VENUE"));

        // Generates the separator line exactly matching the length of the columns block
        table.append("-".repeat(95)).append("\n");

        // 2. Loop through calculated items and safely map them into matching aligned plain-text spaces
        for (TimetableDto.Response slot : slots) {
            table.append(String.format("%-12d | %-12s | %-25s | %-10s | %-10s | %-10s | %-8s\n",
                    slot.getTimetableId(),
                    slot.getCourseCode(),
                    slot.getCourseName(),
                    slot.getDayOfWeek(),
                    slot.getStartTime().toString(),
                    slot.getEndTime().toString(),
                    slot.getVenue()));
        }

        // Log trace at successful response point
        log.info("Successfully processed getStudentSchedule endpoint request for studentId: {}", studentId);
        return ResponseEntity.ok(table.toString());
    }
}