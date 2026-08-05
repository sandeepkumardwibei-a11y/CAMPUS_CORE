package com.campuscore.repository;

import com.campuscore.entity.FacultyAttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FacultyAttendanceRepository extends JpaRepository<FacultyAttendanceRecord, Long> {
    List<FacultyAttendanceRecord> findByFacultyUserId(Long facultyId);

    // Used to block a second faculty-attendance entry for the SAME faculty on the SAME date.
    boolean existsByFacultyUserIdAndDate(Long facultyId, LocalDate date);
}