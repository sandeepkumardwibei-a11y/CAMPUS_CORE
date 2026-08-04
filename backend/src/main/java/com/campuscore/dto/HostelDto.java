package com.campuscore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class HostelDto {

    // ==========================
    // ROOM DTOs
    // ==========================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomResponse {
        private Long roomId;
        private String hostelBlock;
        private String roomNumber;
        private Integer capacity;
        private Integer occupiedCount;
        private Integer availableBeds;
        private String roomType;
        private String status;
        // Names of the students currently staying in this room (ACTIVE allotments).
        @Builder.Default
        private java.util.List<String> occupants = new java.util.ArrayList<>();
    }

    // ==========================
    // HOSTEL APPLICATION DTOs
    // ==========================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HostelApplicationRequest {
        @NotBlank(message = "Reason is required")
        private String reason;

        @NotBlank(message = "Room type is required (SINGLE, DOUBLE, TRIPLE)")
        private String roomType;

        // 1 = 1st year, 2 = 2nd year, ... Optional; defaults to 1 if omitted.
        private Integer studyYear;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HostelApplicationResponse {
        private Long applicationId;
        private Long studentId;
        private String studentName;
        private String reason;
        private String roomType;
        private Integer studyYear;
        private LocalDate applicationDate;
        private String status;
        private Double hostelFee;
        private String paymentStatus;
        private String paymentMode; // 🎯 Tracks chosen payment configuration (CASH, ONLINE, etc.)
        private String rejectionReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectRequest {
        private String reason;
    }

    /**
     * 🎯 NEW HOSTEL PAYMENT REQUEST
     * Enforces strict validation on incoming payment requests inside the hostel module.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HostelPaymentRequest {
        @NotBlank(message = "Payment mode is required (CASH, ONLINE, CARD, NEFT)")
        private String paymentMode;
    }

    // ==========================
    // ROOM ALLOTMENT DTOs
    // ==========================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllotmentRequest {
        @NotNull(message = "Student ID is required")
        private Long studentId;

        @NotNull(message = "Room ID is required")
        private Long roomId;

        @NotBlank(message = "Academic year is required")
        private String academicYear;

        @NotNull(message = "Check-in date is required")
        private LocalDate checkinDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllotmentResponse {
        private Long allotmentId;
        private Long studentId;
        private String studentName;
        private Long roomId;
        private String hostelBlock;
        private String roomNumber;
        private String academicYear;
        private LocalDate checkinDate;
        private LocalDate checkoutDate;
        private String status;
    }

    // ==========================
    // FACILITY BOOKING DTOs
    // ==========================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingRequest {
        @NotBlank
        private String facilityName;

        @NotNull
        private LocalDate bookingDate;

        @NotNull
        private java.time.LocalTime startTime;

        @NotNull
        private java.time.LocalTime endTime;

        private String purpose;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingResponse {
        private Long bookingId;
        private String facilityName;
        private Long bookedById;
        private String bookedByName;
        private LocalDate bookingDate;
        private java.time.LocalTime startTime;
        private java.time.LocalTime endTime;
        private String purpose;
        private String status;
    }
}