package com.campuscore.repository;

import com.campuscore.entity.HostelAllotment;
import com.campuscore.entity.HostelAllotment.AllotmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HostelAllotmentRepository extends JpaRepository<HostelAllotment, Long> {
    Optional<HostelAllotment> findByStudentUserIdAndAcademicYear(Long studentId, String academicYear);
    List<HostelAllotment> findByRoomRoomIdAndStatus(Long roomId, AllotmentStatus status);
    List<HostelAllotment> findByStudentUserId(Long studentId);
}
