package com.campuscore.service;

import com.campuscore.dto.DepartmentDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.Department;
import com.campuscore.entity.Notification.NotificationCategory;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public DepartmentDto.Response createDepartment(DepartmentDto.CreateRequest request) {
        log.info("Entering createDepartment validation routine for departmentName: {}", request.getDepartmentName());

        // RULE: Check if the department name already exists anywhere in the database
        String sanitizedName = request.getDepartmentName().trim();
        if (departmentRepository.existsByDepartmentNameIgnoreCase(sanitizedName)) {
            throw new ResourceNotFoundException("The department already exists.");
        }

        Department department = Department.builder()
                .departmentName(sanitizedName)
                .status("ACTIVE")
                .build();

        departmentRepository.save(department);

        eventPublisher.publishEvent(new NotificationDto.Event(
            null,
            String.format("Academic Restructure: A new department '%s' has been successfully registered.",
                department.getDepartmentName()),
            NotificationCategory.DEPARTMENT
        ));

        log.info("Successfully registered new department entity with ID: {}", department.getDepartmentId());
        return toResponse(department);
    }

    @Transactional(readOnly = true)
    public java.util.List<DepartmentDto.Response> getAll() {
        log.info("Fetching all departments");
        return departmentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    private DepartmentDto.Response toResponse(Department d) {
        return DepartmentDto.Response.builder()
                .departmentId(d.getDepartmentId())
                .departmentName(d.getDepartmentName())
                .status(d.getStatus())
                .build();
    }
}
