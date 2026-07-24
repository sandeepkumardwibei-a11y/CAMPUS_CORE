package com.campuscore.repository;

import com.campuscore.entity.ExamSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {
    Page<ExamSchedule> findByAcademicYearAndSemester(String academicYear, Integer semester, Pageable pageable);
    List<ExamSchedule> findByCourseCourseIdAndAcademicYear(Long courseId, String academicYear);
    List<ExamSchedule> findByExamDateBetween(LocalDate from, LocalDate to);
}
