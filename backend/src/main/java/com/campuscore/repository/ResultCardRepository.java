package com.campuscore.repository;

import com.campuscore.entity.ResultCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResultCardRepository extends JpaRepository<ResultCard, Long> {
    Optional<ResultCard> findByStudentUserIdAndAcademicYearAndSemester(
            Long studentId, String academicYear, Integer semester);
    List<ResultCard> findByStudentUserId(Long studentId);
}
