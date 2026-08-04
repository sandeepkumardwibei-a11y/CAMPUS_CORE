package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.DepartmentDto;
import com.campuscore.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')") //  Encapsulation rule: Strict Admin access restriction
    public ResponseEntity<ApiResponse<DepartmentDto.Response>> createDepartment(@Valid @RequestBody DepartmentDto.CreateRequest request) {
        // Log trace at request entry point
        log.info("Processing createDepartment endpoint request");
        
        DepartmentDto.Response response = departmentService.createDepartment(request);
        
        // Log trace at successful response point
        log.info("Successfully processed createDepartment endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Department created successfully under the program"));
    }

    // GET ALL DEPARTMENTS
    // Intentionally NOT restricted with @PreAuthorize: any authenticated user
    // may view the list of departments. Only creation is admin-only.
    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<DepartmentDto.Response>>> getAllDepartments() {
        log.info("Processing getAllDepartments endpoint request");
        java.util.List<DepartmentDto.Response> response = departmentService.getAll();
        log.info("Successfully processed getAllDepartments endpoint request, count: {}", response.size());
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched all departments successfully"));
    }

    // UPDATE DEPARTMENT STATUS (ACTIVE <-> DISCONTINUED)
    // Admin-only: mirrors the program status toggle. The target status is passed
    // as a query param, e.g. PUT /departments/1/status?status=DISCONTINUED
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')") // Encapsulation rule: Strict Admin access restriction
    public ResponseEntity<ApiResponse<DepartmentDto.Response>> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        log.info("Processing updateStatus endpoint request for departmentId: {} with target status: {}", id, status);

        DepartmentDto.Response response = departmentService.updateStatus(id, status);

        log.info("Successfully processed updateStatus endpoint request for departmentId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Department status updated successfully"));
    }
}