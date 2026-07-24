package com.campuscore.repository;

import com.campuscore.entity.AttendanceSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSummaryRepository extends JpaRepository<AttendanceSummary, Long> {
    Optional<AttendanceSummary> findByStudentUserIdAndCourseCourseIdAndSemesterAndAcademicYear(
            Long studentId, Long courseId, Integer semester, String academicYear);
    List<AttendanceSummary> findByStudentUserIdAndAcademicYear(Long studentId, String academicYear);
    List<AttendanceSummary> findByStudentUserIdAndShortageFlagTrue(Long studentId);
    List<AttendanceSummary> findByCourseCourseIdAndShortageFlagTrue(Long courseId);
}
