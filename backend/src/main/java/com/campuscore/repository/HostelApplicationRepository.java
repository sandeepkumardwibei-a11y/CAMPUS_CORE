package com.campuscore.repository;

import com.campuscore.entity.HostelApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HostelApplicationRepository
        extends JpaRepository<HostelApplication, Long> {

    Optional<HostelApplication>
    findByStudentUserIdAndStatus(
            Long studentId,
            HostelApplication.ApplicationStatus status);

    // All applications for a student (used for re-apply pricing + the student's own list).
    List<HostelApplication> findByStudentUserId(Long studentId);
}