package com.campuscore.service;

import com.campuscore.dto.AcademicStandingDto;
import com.campuscore.entity.ResultCard;
import com.campuscore.entity.User;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.ResultCardRepository;
import com.campuscore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes an automatic academic-standing ranking for students based on the CGPA
 * recorded on their most recent result card.
 *
 *   CGPA > 9         -> EXCELLENT
 *   8 <= CGPA <= 9   -> GOOD
 *   5 <= CGPA < 8    -> AVERAGE
 *   CGPA < 5         -> POOR
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicStandingService {

    private final ResultCardRepository resultRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AcademicStandingDto.Response getForStudent(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", studentId));

        List<ResultCard> cards = resultRepository.findByStudentUserId(studentId);
        ResultCard latest = latestCard(cards);
        return buildResponse(student, latest);
    }

    @Transactional(readOnly = true)
    public List<AcademicStandingDto.Response> getAll() {
        List<User> students = userRepository.findByRole(User.Role.STUDENT);
        List<AcademicStandingDto.Response> out = new ArrayList<>();
        for (User s : students) {
            ResultCard latest = latestCard(resultRepository.findByStudentUserId(s.getUserId()));
            out.add(buildResponse(s, latest));
        }
        // Sort best CGPA first so the list doubles as a ranking board
        out.sort(Comparator.comparing(
                (AcademicStandingDto.Response r) -> r.getCgpa() == null ? BigDecimal.valueOf(-1) : r.getCgpa())
                .reversed());
        return out;
    }

    private ResultCard latestCard(List<ResultCard> cards) {
        if (cards == null || cards.isEmpty()) return null;
        return cards.stream()
                .max(Comparator
                        .comparing(ResultCard::getAcademicYear, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ResultCard::getSemester, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private AcademicStandingDto.Response buildResponse(User student, ResultCard card) {
        BigDecimal cgpa = card != null ? card.getCgpa() : null;
        BigDecimal sgpa = card != null ? card.getSgpa() : null;

        String ranking = rank(cgpa);
        String remark = remark(ranking, student.getName());

        return AcademicStandingDto.Response.builder()
                .studentId(student.getUserId())
                .studentName(student.getName())
                .academicYear(card != null ? card.getAcademicYear() : null)
                .semester(card != null ? card.getSemester() : null)
                .cgpa(cgpa)
                .sgpa(sgpa)
                .ranking(ranking)
                .remark(remark)
                .build();
    }

    private String rank(BigDecimal cgpa) {
        if (cgpa == null) return "NOT_AVAILABLE";
        double v = cgpa.doubleValue();
        if (v > 9.0) return "EXCELLENT";
        if (v >= 8.0) return "GOOD";
        if (v >= 5.0) return "AVERAGE";
        return "POOR";
    }

    private String remark(String ranking, String name) {
        switch (ranking) {
            case "EXCELLENT": return "Outstanding performance — keep it up!";
            case "GOOD":      return "Good academic standing.";
            case "AVERAGE":   return "Satisfactory — there is room for improvement.";
            case "POOR":      return "Needs immediate attention and academic support.";
            default:          return "No published results yet.";
        }
    }
}
