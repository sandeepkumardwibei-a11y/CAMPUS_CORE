package com.campuscore.repository;

import com.campuscore.entity.FeePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeePaymentRepository extends JpaRepository<FeePayment, Long> {
    List<FeePayment> findByInvoice_InvoiceId(Long invoiceId);
    List<FeePayment> findByStatusOrderByCreatedAtDesc(FeePayment.PaymentStatus status);
}
