package com.campuscore.repository;

import com.campuscore.entity.AcademicStanding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AcademicStandingRepository extends JpaRepository<AcademicStanding, Long> {
    Optional<AcademicStanding> findByStudent_UserIdAndAcademicYearAndSemester(Long studentId, String academicYear, Integer semester);
}
