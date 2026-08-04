package com.campuscore.repository;

import com.campuscore.entity.HostelRoom;
import com.campuscore.entity.HostelRoom.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HostelRoomRepository extends JpaRepository<HostelRoom, Long> {
    List<HostelRoom> findByStatus(RoomStatus status);
    Page<HostelRoom> findByHostelBlock(String hostelBlock, Pageable pageable);
    long countByStatus(RoomStatus status);

    // Used to reject duplicate rooms with a friendly message (block + room number must be unique).
    boolean existsByHostelBlockIgnoreCaseAndRoomNumberIgnoreCase(String hostelBlock, String roomNumber);
}
