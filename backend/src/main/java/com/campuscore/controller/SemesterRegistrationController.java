package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.SemesterRegistrationDto;
import com.campuscore.service.SemesterRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@RestController
@RequestMapping("/registrations")
@RequiredArgsConstructor
public class SemesterRegistrationController {

    private final SemesterRegistrationService registrationService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('STUDENT', 'ROLE_STUDENT')")
    public ResponseEntity<ApiResponse<SemesterRegistrationDto.Response>> register(
            @RequestBody SemesterRegistrationDto.CreateRequest request) {
        // Log trace at request entry point
        log.info("Processing register endpoint request");
        
        SemesterRegistrationDto.Response response = registrationService.register(request);
        
        // Log trace at successful response point
        log.info("Successfully processed register endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Semester registration submitted successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN', 'STUDENT', 'ROLE_STUDENT', 'FACULTY', 'ROLE_FACULTY', 'EXAM_CONTROLLER', 'ROLE_EXAM_CONTROLLER')")
    public ResponseEntity<ApiResponse<SemesterRegistrationDto.Response>> getById(@PathVariable Long id) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getById endpoint request for registrationId: {}", id);
        
        SemesterRegistrationDto.Response response = registrationService.getById(id);
        
        // Log trace at successful response point
        log.info("Successfully processed getById endpoint request for registrationId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched registration details"));
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN', 'EXAM_CONTROLLER', 'ROLE_EXAM_CONTROLLER')")
    public ResponseEntity<ApiResponse<List<SemesterRegistrationDto.Response>>> getByStudent(@PathVariable Long studentId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getByStudent endpoint request for studentId: {}", studentId);
        
        List<SemesterRegistrationDto.Response> response = registrationService.getByStudent(studentId);
        
        // Log trace at successful response point
        log.info("Successfully processed getByStudent endpoint request for studentId: {}", studentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched registrations for student"));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN', 'EXAM_CONTROLLER', 'ROLE_EXAM_CONTROLLER')")
    public ResponseEntity<ApiResponse<List<SemesterRegistrationDto.Response>>> getAll() {
        // Log trace at request entry point
        log.info("Processing getAll endpoint request");
        
        List<SemesterRegistrationDto.Response> response = registrationService.getAll();
        
        // Log trace at successful response point
        log.info("Successfully processed getAll endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched all registrations"));
    }

    /**
     * UPDATED ENDPOINT:
     * Now accepts only the courseId path variable. Query parameters for 
     * academicYear and semester have been removed to keep Swagger inputs clean.
     */
    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN', 'FACULTY', 'ROLE_FACULTY', 'EXAM_CONTROLLER', 'ROLE_EXAM_CONTROLLER')")
    public ResponseEntity<ApiResponse<List<SemesterRegistrationDto.Response>>> getByCourse(@PathVariable Long courseId) {
        // Log trace at request entry point using the clean path variable
        log.info("Processing getByCourse endpoint request solely for courseId: {}", courseId);
        
        List<SemesterRegistrationDto.Response> response = registrationService.getByCourse(courseId);
        
        // Log trace at successful response point
        log.info("Successfully processed getByCourse endpoint request for courseId: {}", courseId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched registrations for course"));
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN', 'EXAM_CONTROLLER', 'ROLE_EXAM_CONTROLLER')")
    public ResponseEntity<ApiResponse<SemesterRegistrationDto.Response>> confirmRegistration(@PathVariable Long id) {
        // Log trace at request entry point using safe path variable
        log.info("Processing confirmRegistration endpoint request for registrationId: {}", id);
        
        SemesterRegistrationDto.Response response = registrationService.confirmRegistration(id);
        
        // Log trace at successful response point
        log.info("Successfully processed confirmRegistration endpoint request for registrationId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Registration confirmed successfully"));
    }
}