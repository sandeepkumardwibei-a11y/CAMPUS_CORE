package com.campuscore.service;

import com.campuscore.dto.AcademicStandingDto;
import com.campuscore.entity.ResultCard;
import com.campuscore.entity.User;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.ResultCardRepository;
import com.campuscore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicStandingServiceTest {

    @Mock
    private ResultCardRepository resultRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AcademicStandingService academicStandingService;

    private User sampleStudent1;
    private User sampleStudent2;
    private ResultCard olderResultCard;
    private ResultCard latestResultCard;

    @BeforeEach
    void setUp() {
        sampleStudent1 = new User();
        sampleStudent1.setUserId(101L);
        sampleStudent1.setName("Alice Johnson");
        sampleStudent1.setRole(User.Role.STUDENT);

        sampleStudent2 = new User();
        sampleStudent2.setUserId(102L);
        sampleStudent2.setName("Bob Smith");
        sampleStudent2.setRole(User.Role.STUDENT);

        olderResultCard = new ResultCard();
        olderResultCard.setResultId(1L); // 🎯 Updated to setResultId to match ResultCard entity
        olderResultCard.setStudent(sampleStudent1);
        olderResultCard.setAcademicYear("2025-26");
        olderResultCard.setSemester(1);
        olderResultCard.setCgpa(BigDecimal.valueOf(8.5));
        olderResultCard.setSgpa(BigDecimal.valueOf(8.5));

        latestResultCard = new ResultCard();
        latestResultCard.setResultId(2L); // 🎯 Updated to setResultId to match ResultCard entity
        latestResultCard.setStudent(sampleStudent1);
        latestResultCard.setAcademicYear("2025-26");
        latestResultCard.setSemester(2);
        latestResultCard.setCgpa(BigDecimal.valueOf(9.4));
        latestResultCard.setSgpa(BigDecimal.valueOf(9.6));
    }

    // ─────────────────────────────────────────────────────────
    // 1. GET FOR SINGLE STUDENT TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getForStudent_Success_SelectsLatestCard() {
        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleStudent1));
        when(resultRepository.findByStudentUserId(101L)).thenReturn(List.of(olderResultCard, latestResultCard));

        AcademicStandingDto.Response response = academicStandingService.getForStudent(101L);

        assertNotNull(response);
        assertEquals(101L, response.getStudentId());
        assertEquals("Alice Johnson", response.getStudentName());
        assertEquals("2025-26", response.getAcademicYear());
        assertEquals(2, response.getSemester());
        assertEquals(BigDecimal.valueOf(9.4), response.getCgpa());
        assertEquals("EXCELLENT", response.getRanking());
        assertEquals("Outstanding performance — keep it up!", response.getRemark());
    }

    @Test
    void getForStudent_Success_WhenNoResultCardsExist() {
        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleStudent1));
        when(resultRepository.findByStudentUserId(101L)).thenReturn(Collections.emptyList());

        AcademicStandingDto.Response response = academicStandingService.getForStudent(101L);

        assertNotNull(response);
        assertEquals(101L, response.getStudentId());
        assertNull(response.getCgpa());
        assertNull(response.getAcademicYear());
        assertEquals("NOT_AVAILABLE", response.getRanking());
        assertEquals("No published results yet.", response.getRemark());
    }

    @Test
    void getForStudent_ThrowsException_WhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> academicStandingService.getForStudent(999L));
        verify(resultRepository, never()).findByStudentUserId(anyLong());
    }

    // ─────────────────────────────────────────────────────────
    // 2. GET ALL & LEADERBOARD SORTING TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getAll_Success_SortsDescendingByCgpa() {
        ResultCard bobCard = new ResultCard();
        bobCard.setResultId(3L); // 🎯 Updated to setResultId
        bobCard.setStudent(sampleStudent2);
        bobCard.setAcademicYear("2025-26");
        bobCard.setSemester(2);
        bobCard.setCgpa(BigDecimal.valueOf(7.2));

        when(userRepository.findByRole(User.Role.STUDENT)).thenReturn(List.of(sampleStudent2, sampleStudent1));
        when(resultRepository.findByStudentUserId(101L)).thenReturn(List.of(latestResultCard));
        when(resultRepository.findByStudentUserId(102L)).thenReturn(List.of(bobCard));

        List<AcademicStandingDto.Response> results = academicStandingService.getAll();

        assertNotNull(results);
        assertEquals(2, results.size());

        // 🎯 Uses getFirst() to eliminate the IDE warning
        assertEquals(101L, results.getFirst().getStudentId());
        assertEquals(BigDecimal.valueOf(9.4), results.getFirst().getCgpa());
        assertEquals("EXCELLENT", results.getFirst().getRanking());

        assertEquals(102L, results.get(1).getStudentId());
        assertEquals(BigDecimal.valueOf(7.2), results.get(1).getCgpa());
        assertEquals("AVERAGE", results.get(1).getRanking());
    }

    @Test
    void getAll_PlacesStudentsWithoutCardsAtTheEnd() {
        when(userRepository.findByRole(User.Role.STUDENT)).thenReturn(List.of(sampleStudent2, sampleStudent1));
        when(resultRepository.findByStudentUserId(101L)).thenReturn(List.of(latestResultCard));
        when(resultRepository.findByStudentUserId(102L)).thenReturn(Collections.emptyList());

        List<AcademicStandingDto.Response> results = academicStandingService.getAll();

        assertNotNull(results);
        assertEquals(2, results.size());

        assertEquals(101L, results.getFirst().getStudentId());
        assertEquals(102L, results.get(1).getStudentId());
        assertNull(results.get(1).getCgpa());
    }

    // ─────────────────────────────────────────────────────────
    // 3. CGPA RANKING & REMARK BOUNDARY TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void rankAndRemark_GoodStandingBoundary() {
        ResultCard goodCard = new ResultCard();
        goodCard.setCgpa(BigDecimal.valueOf(8.0));

        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleStudent1));
        when(resultRepository.findByStudentUserId(101L)).thenReturn(List.of(goodCard));

        AcademicStandingDto.Response response = academicStandingService.getForStudent(101L);

        assertEquals("GOOD", response.getRanking());
        assertEquals("Good academic standing.", response.getRemark());
    }

    @Test
    void rankAndRemark_AverageStandingBoundary() {
        ResultCard avgCard = new ResultCard();
        avgCard.setCgpa(BigDecimal.valueOf(5.0));

        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleStudent1));
        when(resultRepository.findByStudentUserId(101L)).thenReturn(List.of(avgCard));

        AcademicStandingDto.Response response = academicStandingService.getForStudent(101L);

        assertEquals("AVERAGE", response.getRanking());
        assertEquals("Satisfactory — there is room for improvement.", response.getRemark());
    }

    @Test
    void rankAndRemark_PoorStandingBoundary() {
        ResultCard poorCard = new ResultCard();
        poorCard.setCgpa(BigDecimal.valueOf(4.99));

        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleStudent1));
        when(resultRepository.findByStudentUserId(101L)).thenReturn(List.of(poorCard));

        AcademicStandingDto.Response response = academicStandingService.getForStudent(101L);

        assertEquals("POOR", response.getRanking());
        assertEquals("Needs immediate attention and academic support.", response.getRemark());
    }
}