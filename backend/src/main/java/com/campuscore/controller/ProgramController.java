package com.campuscore.controller;

import com.campuscore.dto.ApiResponse; 
import com.campuscore.dto.ProgramDto;
import com.campuscore.service.ProgramService;
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
@RequestMapping("/programs")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    // CREATE PROGRAM (Admin Authorized)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProgramDto.Response>> createProgram(@Valid @RequestBody ProgramDto.CreateRequest request) {
        // Log trace at request entry point
        log.info("Processing createProgram endpoint request");
        
        ProgramDto.Response response = programService.createProgram(request);
        
        // Log trace at successful response point
        log.info("Successfully processed createProgram endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Program created successfully"));
    }

    // 🎯 GET ALL PROGRAMS (No parameters, strictly returns the database array)
    @GetMapping
    @Operation(summary = "Get all academic programs", description = "Fetches a comprehensive list of all active programs in the system.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully fetched all programs",
        content = @Content(schema = @Schema(implementation = ProgramDto.Response.class))) // 🔥 Forces Swagger to drop the old registration schema cache!
    public ResponseEntity<ApiResponse<List<ProgramDto.Response>>> getAllPrograms() {
        // Log trace at request entry point
        log.info("Processing getAllPrograms endpoint request");
        
        List<ProgramDto.Response> response = programService.getAll();
        
        // Log trace at successful response point
        log.info("Successfully processed getAllPrograms endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched all programs successfully"));
    }

    // 🎯 GET PROGRAM BY ID (Search and retrieve a single program configuration)
    @GetMapping("/{id}")
    @Operation(summary = "Get program by unique ID", description = "Provide an ID to fetch specific entry requirements and course tracking data.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully fetched program details",
        content = @Content(schema = @Schema(implementation = ProgramDto.Response.class))) // 🔥 Forces Swagger to drop the old registration schema cache!
    public ResponseEntity<ApiResponse<ProgramDto.Response>> getProgramById(@PathVariable Long id) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getProgramById endpoint request for programId: {}", id);
        
        ProgramDto.Response response = programService.getById(id);
        
        // Log trace at successful response point
        log.info("Successfully processed getProgramById endpoint request for programId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched program details successfully"));
    }

    // UPDATE PROGRAM STATUS (Admin Authorized)
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProgramDto.Response>> updateStatus(
            @PathVariable Long id, 
            @RequestParam String status) {
        // Log trace at request entry point using safe parameters
        log.info("Processing updateStatus endpoint request for programId: {} with target status: {}", id, status);
        
        ProgramDto.Response response = programService.updateStatus(id, status);
        
        // Log trace at successful response point
        log.info("Successfully processed updateStatus endpoint request for programId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Program status updated successfully"));
    }
}