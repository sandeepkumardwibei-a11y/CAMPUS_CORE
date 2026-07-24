package com.campuscore.repository;

import com.campuscore.entity.FacilityBooking;
import com.campuscore.entity.FacilityBooking.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FacilityBookingRepository extends JpaRepository<FacilityBooking, Long> {
    Page<FacilityBooking> findByStatus(BookingStatus status, Pageable pageable);
    List<FacilityBooking> findByBookedByUserIdOrderByBookingDateDesc(Long userId);
    List<FacilityBooking> findByFacilityNameAndBookingDate(String facilityName, LocalDate date);
}
