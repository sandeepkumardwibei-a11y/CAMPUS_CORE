package com.campuscore.repository;

import com.campuscore.entity.HostelApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HostelApplicationRepository
        extends JpaRepository<HostelApplication, Long> {

    Optional<HostelApplication>
    findByStudentUserIdAndStatus(
            Long studentId,
            HostelApplication.ApplicationStatus status);
}