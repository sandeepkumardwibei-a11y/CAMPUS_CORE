package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.HostelDto;
import com.campuscore.service.FacilityBookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class FacilityBookingController {

    private final FacilityBookingService bookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<HostelDto.BookingResponse>> bookFacility(
            @RequestBody HostelDto.BookingRequest request,
            @RequestParam Long userId) {
        log.info("Processing bookFacility endpoint request for userId: {}", userId);
        
        HostelDto.BookingResponse response = bookingService.bookFacility(request, userId);
        
        log.info("Successfully processed bookFacility endpoint request for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Facility booking processed successfully"));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<HostelDto.BookingResponse>>> getUserBookings(@PathVariable Long userId) {
        log.info("Processing getUserBookings endpoint request for userId: {}", userId);
        
        List<HostelDto.BookingResponse> response = bookingService.getUserBookings(userId);
        
        log.info("Successfully processed getUserBookings endpoint request for userId: {}", userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched user bookings"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<HostelDto.BookingResponse>>> getAllBookings() {
        log.info("Processing getAllBookings endpoint request");
        
        List<HostelDto.BookingResponse> response = bookingService.getAllBookings();
        
        log.info("Successfully processed getAllBookings endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched all bookings"));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HostelDto.BookingResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        log.info("Processing updateStatus endpoint request for bookingId: {} with target status: {}", id, status);
        
        HostelDto.BookingResponse response = bookingService.updateStatus(id, status);
        
        log.info("Successfully processed updateStatus endpoint request for bookingId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Booking status updated successfully"));
    }
}