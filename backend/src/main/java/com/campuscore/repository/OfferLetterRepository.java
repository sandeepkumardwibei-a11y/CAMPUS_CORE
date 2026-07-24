package com.campuscore.repository;

import com.campuscore.entity.AdmissionApplication;
import com.campuscore.entity.OfferLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OfferLetterRepository extends JpaRepository<OfferLetter, Long> {
    // 🔥 Make sure this exists so the Service layer can query offer letters by object relationships!
    Optional<OfferLetter> findByApplication(AdmissionApplication application);
}