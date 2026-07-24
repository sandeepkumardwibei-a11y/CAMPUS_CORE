package com.campuscore.repository;

import com.campuscore.entity.FeeInvoice;
import com.campuscore.entity.FeeInvoice.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeeInvoiceRepository extends JpaRepository<FeeInvoice, Long> {
    Optional<FeeInvoice> findByStudentUserIdAndAcademicYearAndSemester(
            Long studentId, String academicYear, Integer semester);
    List<FeeInvoice> findByStudentUserId(Long studentId);
    Page<FeeInvoice> findByStatus(InvoiceStatus status, Pageable pageable);
    List<FeeInvoice> findByStatusAndAcademicYear(InvoiceStatus status, String academicYear);
}
