package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.CourseDto;
import com.campuscore.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    // CREATE COURSE
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseDto.Response>> createCourse(@Valid @RequestBody CourseDto.CreateRequest request) {
        // Log trace at request entry point
        log.info("Processing createCourse endpoint request");
        
        CourseDto.Response response = courseService.createCourse(request);
        
        // Log trace at successful response point
        log.info("Successfully processed createCourse endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Course created successfully"));
    }

    // GET ALL COURSES
    @GetMapping
    @Operation(summary = "Get all master courses")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CourseDto.Response.class)))
    public ResponseEntity<ApiResponse<List<CourseDto.Response>>> getAllCourses() {
        // Log trace at request entry point
        log.info("Processing getAllCourses endpoint request");
        
        List<CourseDto.Response> response = courseService.getAll();
        
        // Log trace at successful response point
        log.info("Successfully processed getAllCourses endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched all courses"));
    }

    // GET COURSE BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get course details by ID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CourseDto.Response.class)))
    public ResponseEntity<ApiResponse<CourseDto.Response>> getCourseById(@PathVariable Long id) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getCourseById endpoint request for courseId: {}", id);
        
        CourseDto.Response response = courseService.getById(id);
        
        // Log trace at successful response point
        log.info("Successfully processed getCourseById endpoint request for courseId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched course details"));
    }

    // GET COURSES BY PROGRAM ID (And optional Semester query filter)
    @GetMapping("/program/{programId}")
    @Operation(summary = "Get courses linked to a program")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CourseDto.Response.class)))
    public ResponseEntity<ApiResponse<List<CourseDto.Response>>> getCoursesByProgram(
            @PathVariable Long programId,
            @RequestParam(required = false) Integer semester) {
        // Log trace at request entry point using safe variables
        log.info("Processing getCoursesByProgram endpoint request for programId: {} and semester: {}", programId, semester);
        
        List<CourseDto.Response> response;
        if (semester != null) {
            response = courseService.getByProgramAndSemester(programId, semester);
        } else {
            response = courseService.getByProgram(programId);
        }
        
        // Log trace at successful response point
        log.info("Successfully processed getCoursesByProgram endpoint request for programId: {}", programId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched courses successfully"));
    }

    // GET COURSES ASSIGNED TO A FACULTY MEMBER
    @GetMapping("/faculty/{facultyId}")
    @Operation(summary = "Get courses assigned to a faculty instructor")
    public ResponseEntity<ApiResponse<List<CourseDto.Response>>> getCoursesByFaculty(@PathVariable Long facultyId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getCoursesByFaculty endpoint request for facultyId: {}", facultyId);
        
        List<CourseDto.Response> response = courseService.getByFaculty(facultyId);
        
        // Log trace at successful response point
        log.info("Successfully processed getCoursesByFaculty endpoint request for facultyId: {}", facultyId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched courses by faculty"));
    }

    // ASSIGN FACULTY TO COURSE
    @PutMapping("/{id}/assign-faculty")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseDto.Response>> assignFaculty(@PathVariable Long id, @RequestParam Long facultyId) {
        // Log trace at request entry point using safe parameters
        log.info("Processing assignFaculty endpoint request for courseId: {} and facultyId: {}", id, facultyId);
        
        CourseDto.Response response = courseService.assignFaculty(id, facultyId);
        
        // Log trace at successful response point
        log.info("Successfully processed assignFaculty endpoint request for courseId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Faculty assigned successfully"));
    }

    // UPDATE COURSE STATUS
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseDto.Response>> updateStatus(@PathVariable Long id, @RequestParam String status) {
        // Log trace at request entry point using safe parameters
        log.info("Processing updateStatus endpoint request for courseId: {} with target status: {}", id, status);
        
        CourseDto.Response response = courseService.updateStatus(id, status);
        
        // Log trace at successful response point
        log.info("Successfully processed updateStatus endpoint request for courseId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Course status updated successfully"));
    }
}