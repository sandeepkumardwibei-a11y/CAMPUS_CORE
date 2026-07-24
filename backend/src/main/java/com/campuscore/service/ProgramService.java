package com.campuscore.service;

import com.campuscore.dto.ProgramDto;
import com.campuscore.entity.Department;
import com.campuscore.entity.Program;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.DepartmentRepository;
import com.campuscore.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProgramService {

    private final ProgramRepository programRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProgramDto.Response createProgram(ProgramDto.CreateRequest request) {
        log.info("Attempting to create a new program with name: {}", request.getProgramName());

        // 1. Check if Program Name already exists
        if (request.getProgramName() != null && programRepository.existsByProgramNameIgnoreCase(request.getProgramName().trim())) {
            log.warn("Program creation failed: A program with name '{}' already exists", request.getProgramName());
            throw new ResourceNotFoundException("The program already exists.");
        }

        // 2. Validate the selected department (one department -> many programs)
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("The selected department does not exist."));

        // 3. Build and save the program entity safely
        Program program = Program.builder()
                .programName(request.getProgramName().trim())
                .departmentId(department.getDepartmentId())
                .level(Program.Level.valueOf(request.getLevel().toUpperCase()))
                .durationYears(request.getDurationYears())
                .totalSeats(request.getTotalSeats() != null ? request.getTotalSeats() : 60)
                .minimumPercentage(request.getMinimumPercentage())
                .status(Program.ProgramStatus.ACTIVE)
                .build();

        programRepository.save(program);
        log.info("Successfully generated and saved new Program record with system assigned ID: {}", program.getProgramId());

        eventPublisher.publishEvent(program);
        return toResponse(program);
    }

    @Transactional(readOnly = true)
    public ProgramDto.Response getById(Long id) {
        log.debug("Fetching program details metadata record for ID: {}", id);
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<ProgramDto.Response> getAll() {
        log.debug("Retrieving entire list collection of registered programs");
        List<Program> programs = programRepository.findAll();
        if (programs == null || programs.isEmpty()) {
            return List.of();
        }
        return programs.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProgramDto.Response updateStatus(Long id, String status) {
        log.info("Executing lifecycle state transition request for Program ID: {} to status: {}", id, status);
        Program program = findOrThrow(id);
        program.setStatus(Program.ProgramStatus.valueOf(status.toUpperCase()));
        programRepository.save(program);
        eventPublisher.publishEvent(program);
        return toResponse(program);
    }

    private Program findOrThrow(Long id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));
    }

    private ProgramDto.Response toResponse(Program p) {
        String deptName = null;
        if (p.getDepartmentId() != null) {
            deptName = departmentRepository.findById(p.getDepartmentId())
                    .map(Department::getDepartmentName).orElse(null);
        }
        return ProgramDto.Response.builder()
                .programId(p.getProgramId())
                .programName(p.getProgramName())
                .departmentId(p.getDepartmentId())
                .departmentName(deptName)
                .level(p.getLevel() != null ? p.getLevel().name() : "UG")
                .durationYears(p.getDurationYears())
                .totalSeats(p.getTotalSeats())
                .minimumPercentage(p.getMinimumPercentage())
                .status(p.getStatus() != null ? p.getStatus().name() : "ACTIVE")
                .build();
    }
}
