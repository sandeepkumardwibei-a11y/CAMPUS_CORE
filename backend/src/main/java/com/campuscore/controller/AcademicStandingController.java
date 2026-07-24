package com.campuscore.controller;

import com.campuscore.dto.AcademicStandingDto;
import com.campuscore.dto.ApiResponse;
import com.campuscore.service.AcademicStandingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/academic-standing")
@RequiredArgsConstructor
public class AcademicStandingController {

    private final AcademicStandingService academicStandingService;

    // ADMIN and FACULTY can see the standing of ALL students.
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
    public ResponseEntity<ApiResponse<List<AcademicStandingDto.Response>>> getAll() {
        log.info("Fetching academic standing for all students");
        List<AcademicStandingDto.Response> response = academicStandingService.getAll();
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched academic standing for all students"));
    }

    // A student may see ONLY their own standing; ADMIN/FACULTY may see anyone's.
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY') or (hasRole('STUDENT') and #studentId == authentication.principal.userId)")
    public ResponseEntity<ApiResponse<AcademicStandingDto.Response>> getForStudent(@PathVariable Long studentId) {
        log.info("Fetching academic standing for studentId: {}", studentId);
        AcademicStandingDto.Response response = academicStandingService.getForStudent(studentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched academic standing"));
    }
}
