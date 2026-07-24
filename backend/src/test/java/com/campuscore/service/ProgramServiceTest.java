package com.campuscore.service;

import com.campuscore.dto.ProgramDto;
import com.campuscore.entity.Department;
import com.campuscore.entity.Program;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.DepartmentRepository;
import com.campuscore.repository.ProgramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgramServiceTest {

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProgramService programService;

    private Department sampleDepartment;
    private Program sampleProgram;

    @BeforeEach
    void setUp() {
        sampleDepartment = Department.builder()
                .departmentId(1L)
                .departmentName("Computer Science")
                .status("ACTIVE")
                .build();

        sampleProgram = Program.builder()
                .programId(10L)
                .programName("Bachelor of Computer Science")
                .departmentId(1L)
                .level(Program.Level.UG)
                .durationYears(4)
                .totalSeats(60)
                .minimumPercentage(60.0)
                .status(Program.ProgramStatus.ACTIVE)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // 1. CREATE PROGRAM TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void createProgram_Success() {
        ProgramDto.CreateRequest request = new ProgramDto.CreateRequest();
        request.setProgramName("Bachelor of Computer Science");
        request.setDepartmentId(1L);
        request.setLevel("UG");
        request.setDurationYears(4);
        request.setTotalSeats(60);
        request.setMinimumPercentage(60.0);

        when(programRepository.existsByProgramNameIgnoreCase("Bachelor of Computer Science")).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(sampleDepartment));
        when(programRepository.save(any(Program.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProgramDto.Response response = programService.createProgram(request);

        assertNotNull(response);
        assertEquals("Bachelor of Computer Science", response.getProgramName());
        assertEquals("Computer Science", response.getDepartmentName());
        assertEquals("UG", response.getLevel());
        verify(programRepository, times(1)).save(any(Program.class));
        verify(eventPublisher, times(1)).publishEvent(any(Program.class));
    }

    @Test
    void createProgram_ThrowsException_WhenProgramNameAlreadyExists() {
        ProgramDto.CreateRequest request = new ProgramDto.CreateRequest();
        request.setProgramName("Bachelor of Computer Science");

        when(programRepository.existsByProgramNameIgnoreCase("Bachelor of Computer Science")).thenReturn(true);

        assertThrows(ResourceNotFoundException.class, () -> programService.createProgram(request));
        verify(programRepository, never()).save(any(Program.class));
    }

    @Test
    void createProgram_ThrowsException_WhenDepartmentNotFound() {
        ProgramDto.CreateRequest request = new ProgramDto.CreateRequest();
        request.setProgramName("New Program");
        request.setDepartmentId(99L);

        when(programRepository.existsByProgramNameIgnoreCase("New Program")).thenReturn(false);
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> programService.createProgram(request));
        verify(programRepository, never()).save(any(Program.class));
    }

    // ─────────────────────────────────────────────────────────
    // 2. READ / QUERY TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getById_Success() {
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(sampleDepartment));

        ProgramDto.Response response = programService.getById(10L);

        assertNotNull(response);
        assertEquals(10L, response.getProgramId());
        assertEquals("Bachelor of Computer Science", response.getProgramName());
    }

    @Test
    void getById_ThrowsException_WhenNotFound() {
        when(programRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> programService.getById(99L));
    }

    @Test
    void getAll_Success() {
        when(programRepository.findAll()).thenReturn(List.of(sampleProgram));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(sampleDepartment));

        List<ProgramDto.Response> result = programService.getAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Bachelor of Computer Science", result.get(0).getProgramName());
    }

    @Test
    void getAll_ReturnsEmptyList_WhenNoProgramsExist() {
        when(programRepository.findAll()).thenReturn(List.of());

        List<ProgramDto.Response> result = programService.getAll();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ─────────────────────────────────────────────────────────
    // 3. UPDATE STATUS TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void updateStatus_Success() {
        Program targetProgram = Program.builder()
                .programId(10L)
                .programName("Bachelor of Computer Science")
                .departmentId(1L)
                .level(Program.Level.UG)
                .durationYears(4)
                .totalSeats(60)
                .minimumPercentage(60.0)
                .status(Program.ProgramStatus.ACTIVE)
                .build();

        when(programRepository.findById(10L)).thenReturn(Optional.of(targetProgram));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(sampleDepartment));
        when(programRepository.save(any(Program.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Updated to use DISCONTINUED which is defined in ProgramStatus enum
        ProgramDto.Response response = programService.updateStatus(10L, "DISCONTINUED");

        assertNotNull(response);
        assertEquals("DISCONTINUED", response.getStatus());
        verify(programRepository, times(1)).save(targetProgram);
        verify(eventPublisher, times(1)).publishEvent(targetProgram);
    }

    @Test
    void updateStatus_ThrowsException_WhenInvalidStatusProvided() {
        when(programRepository.findById(10L)).thenReturn(Optional.of(sampleProgram));

        assertThrows(IllegalArgumentException.class, () -> programService.updateStatus(10L, "INVALID_STATUS"));
    }
}