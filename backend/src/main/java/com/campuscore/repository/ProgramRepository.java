package com.campuscore.repository;

import com.campuscore.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {

    boolean existsByProgramNameIgnoreCase(String programName);

    Optional<Program> findByProgramNameIgnoreCase(String programName);

    // Programs offered under a given department (one dept -> many programs)
    List<Program> findByDepartmentId(Long departmentId);
}
