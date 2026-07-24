package com.campuscore.repository;

import com.campuscore.entity.AdmissionApplication;
import com.campuscore.entity.AdmissionApplication.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdmissionApplicationRepository extends JpaRepository<AdmissionApplication, Long> {
    
    Page<AdmissionApplication> findByStatus(ApplicationStatus status, Pageable pageable);
    
    List<AdmissionApplication> findByEmailIgnoreCase(String email);
    
    Page<AdmissionApplication> findByProgramId(Long programId, Pageable pageable);
}