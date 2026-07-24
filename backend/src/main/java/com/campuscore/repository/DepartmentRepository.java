package com.campuscore.repository;

import com.campuscore.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // Checks if this department name already exists anywhere
    boolean existsByDepartmentNameIgnoreCase(String departmentName);

    // Used by AdmissionService to resolve the department by name
    Optional<Department> findByDepartmentNameIgnoreCase(String departmentName);
}
