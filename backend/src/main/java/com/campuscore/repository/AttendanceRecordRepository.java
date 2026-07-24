package com.campuscore.repository;

import com.campuscore.entity.AttendanceRecord;
import com.campuscore.entity.AttendanceRecord.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByStudentUserIdAndCourseCourseId(Long studentId, Long courseId);
    Optional<AttendanceRecord> findByStudentUserIdAndCourseCourseIdAndLectureDate(
            Long studentId, Long courseId, LocalDate date);
    List<AttendanceRecord> findByCourseCourseIdAndLectureDate(Long courseId, LocalDate date);
    long countByStudentUserIdAndCourseCourseIdAndStatus(Long studentId, Long courseId, AttendanceStatus status);
}
