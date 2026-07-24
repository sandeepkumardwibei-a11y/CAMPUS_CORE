package com.campuscore.repository;

import com.campuscore.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCourseCode(String courseCode);
    Page<Course> findByProgramProgramId(Long programId, Pageable pageable);
    List<Course> findByFacultyUserId(Long facultyId);
    List<Course> findByProgramProgramIdAndSemester(Long programId, Integer semester);

    // Many-to-many: courses that list this program in their programIds collection
    @Query("SELECT c FROM Course c JOIN c.programIds p WHERE p = :programId")
    List<Course> findByProgramIdsContaining(@Param("programId") Long programId);
}
