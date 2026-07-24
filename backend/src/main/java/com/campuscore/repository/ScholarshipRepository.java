package com.campuscore.repository;

import com.campuscore.entity.Scholarship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScholarshipRepository extends JpaRepository<Scholarship, Long> {
    List<Scholarship> findByStudentUserId(Long studentId);
    List<Scholarship> findByStudentUserIdAndAcademicYear(Long studentId, String academicYear);
}
