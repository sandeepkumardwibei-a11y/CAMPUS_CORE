package com.campuscore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "hostel_room",
        uniqueConstraints = @UniqueConstraint(columnNames = {"hostel_block", "room_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HostelRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "hostel_block", nullable = false, length = 50)
    private String hostelBlock;

    @Column(name = "room_number", nullable = false, length = 20)
    private String roomNumber;

    @Builder.Default
    @Column(nullable = false)
    private Integer capacity = 2;

    @Builder.Default
    @Column(name = "occupied_count", nullable = false)
    private Integer occupiedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 10)
    private RoomType roomType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private RoomStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum RoomType { SINGLE, DOUBLE, TRIPLE }
    public enum RoomStatus { AVAILABLE, OCCUPIED, MAINTENANCE }
}