package com.campuscore.service;

import com.campuscore.dto.DepartmentDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.Department;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.DepartmentRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DepartmentService departmentService;

    private Department sampleDepartment;

    @BeforeEach
    void setUp() {
        sampleDepartment = Department.builder()
                .departmentId(1L)
                .departmentName("Computer Science")
                .status("ACTIVE")
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // 1. CREATE DEPARTMENT TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void createDepartment_Success() {
        DepartmentDto.CreateRequest request = new DepartmentDto.CreateRequest();
        request.setDepartmentName("Computer Science");

        when(departmentRepository.existsByDepartmentNameIgnoreCase("Computer Science")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> {
            Department d = invocation.getArgument(0);
            d.setDepartmentId(1L);
            return d;
        });

        DepartmentDto.Response response = departmentService.createDepartment(request);

        assertNotNull(response);
        assertEquals(1L, response.getDepartmentId());
        assertEquals("Computer Science", response.getDepartmentName());
        assertEquals("ACTIVE", response.getStatus());

        verify(departmentRepository, times(1)).save(any(Department.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void createDepartment_ThrowsException_WhenDepartmentAlreadyExists() {
        DepartmentDto.CreateRequest request = new DepartmentDto.CreateRequest();
        request.setDepartmentName("Computer Science");

        when(departmentRepository.existsByDepartmentNameIgnoreCase("Computer Science")).thenReturn(true);

        assertThrows(ResourceNotFoundException.class, () -> departmentService.createDepartment(request));
        verify(departmentRepository, never()).save(any(Department.class));
    }

    // ─────────────────────────────────────────────────────────
    // 2. GET ALL DEPARTMENTS TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getAll_Success() {
        Department dept2 = Department.builder()
                .departmentId(2L)
                .departmentName("Mechanical Engineering")
                .status("ACTIVE")
                .build();

        when(departmentRepository.findAll()).thenReturn(List.of(sampleDepartment, dept2));

        List<DepartmentDto.Response> result = departmentService.getAll();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Computer Science", result.getFirst().getDepartmentName());
        assertEquals("Mechanical Engineering", result.get(1).getDepartmentName());
    }

    @Test
    void getAll_ReturnsEmptyList_WhenNoDepartmentsExist() {
        when(departmentRepository.findAll()).thenReturn(List.of());

        List<DepartmentDto.Response> result = departmentService.getAll();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}