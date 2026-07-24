package com.campuscore.repository;

import com.campuscore.entity.SemesterRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SemesterRegistrationRepository extends JpaRepository<SemesterRegistration, Long> {
    
    Optional<SemesterRegistration> findByStudentUserIdAndProgramProgramIdAndAcademicYearAndSemester(
            Long studentId, Long programId, String academicYear, Integer semester);
            
    List<SemesterRegistration> findByStudentUserId(Long studentId);

    /**
     * OPTIMIZED FETCH QUERY:
     * Pulls semester registrations containing the target courseId. It uses a JOIN FETCH 
     * to populate the lazy-loaded relationship safely in a single database trip.
     */
    @Query("SELECT DISTINCT r FROM SemesterRegistration r JOIN FETCH r.courses c WHERE c.courseId = :courseId")
    List<SemesterRegistration> findByCoursesCourseId(@Param("courseId") Long courseId);
}