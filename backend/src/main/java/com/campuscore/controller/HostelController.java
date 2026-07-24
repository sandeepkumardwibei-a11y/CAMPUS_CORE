package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.HostelDto;
import com.campuscore.service.HostelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.campuscore.entity.User;
import java.util.List;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@RestController
@RequestMapping("/hostel")
@RequiredArgsConstructor
public class HostelController {

    private final HostelService hostelService;

    // HOSTEL ADMIN CREATES ROOM
    @PostMapping("/rooms")
    @PreAuthorize("hasAnyRole('HOSTEL_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<HostelDto.RoomResponse>> createRoom(
            @Valid @RequestBody HostelDto.RoomResponse request) {
        // Log trace at request entry point
        log.info("Processing createRoom endpoint request");

        HostelDto.RoomResponse response = hostelService.createRoom(request);
        
        // Log trace at successful response point
        log.info("Successfully processed createRoom endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Hostel room created successfully"));
    }

    @PostMapping("/applications/{studentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<HostelDto.HostelApplicationResponse>> applyForHostel(
            @PathVariable Long studentId,
            @RequestBody HostelDto.HostelApplicationRequest request) {
        // Log trace at request entry point using safe path variable
        log.info("Processing applyForHostel endpoint request for studentId: {}", studentId);

        HostelDto.HostelApplicationResponse response = hostelService.applyForHostel(studentId, request);
        
        // Log trace at successful response point
        log.info("Successfully processed applyForHostel endpoint request for studentId: {}", studentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Hostel application submitted successfully"));
    }

    @PutMapping("/applications/{applicationId}/approve")
    @PreAuthorize("hasAnyRole('HOSTEL_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<HostelDto.HostelApplicationResponse>> approveApplication(
                    @PathVariable Long applicationId,
                    Authentication authentication) {
        // Log trace at request entry point using safe path variable
        log.info("Processing approveApplication endpoint request for applicationId: {}", applicationId);

        User user = (User) authentication.getPrincipal();
        HostelDto.HostelApplicationResponse response = hostelService.approveApplication(applicationId, user.getUserId());
        
        // Log trace at successful response point
        log.info("Successfully processed approveApplication endpoint request for applicationId: {}", applicationId);
        return ResponseEntity.ok(ApiResponse.success(response, "Hostel application approved successfully"));
    }

    /**
     *  STUDENT DIRECT SELF-PAY ACTION: Process fee payments inside the isolated hostel module.
     * Updated to accept the dynamic payment mode request object.
     */
    @PutMapping("/applications/{applicationId}/pay")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<HostelDto.HostelApplicationResponse>> payHostelFee(
            @PathVariable Long applicationId,
            @Valid @RequestBody HostelDto.HostelPaymentRequest request) {
        // Log trace at request entry point using safe path variable
        log.info("Processing payHostelFee endpoint request for applicationId: {}", applicationId);
        
        HostelDto.HostelApplicationResponse response = hostelService.payHostelFee(applicationId, request);
        String successMessage = "Yes, payment is done! You can take our hostel facility, happy learning!";
        
        // Log trace at successful response point
        log.info("Successfully processed payHostelFee endpoint request for applicationId: {}", applicationId);
        return ResponseEntity.ok(ApiResponse.success(response, successMessage));
    }

    @PutMapping("/applications/{applicationId}/reject")
    @PreAuthorize("hasAnyRole('HOSTEL_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<HostelDto.HostelApplicationResponse>> rejectApplication(
            @PathVariable Long applicationId,
            @RequestBody(required = false) HostelDto.RejectRequest request,
            Authentication authentication) {
        // Log trace at request entry point using safe path variable
        log.info("Processing rejectApplication endpoint request for applicationId: {}", applicationId);

        User user = (User) authentication.getPrincipal();
        String reason = request != null ? request.getReason() : null;
        HostelDto.HostelApplicationResponse response = hostelService.rejectApplication(applicationId, user.getUserId(), reason);
        
        // Log trace at successful response point
        log.info("Successfully processed rejectApplication endpoint request for applicationId: {}", applicationId);
        return ResponseEntity.ok(ApiResponse.success(response, "Hostel application rejected successfully"));
    }

    // ADMIN / HOSTEL ADMIN: list every hostel application (pending, approved, rejected)
    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('HOSTEL_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<List<HostelDto.HostelApplicationResponse>>> getAllApplications() {
        log.info("Processing getAllApplications endpoint request");
        List<HostelDto.HostelApplicationResponse> response = hostelService.getAllApplications();
        log.info("Successfully processed getAllApplications endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched all hostel applications"));
    }

    // ADMIN / HOSTEL ADMIN: list every hostel allotment (who is staying where)
    @GetMapping("/allotments")
    @PreAuthorize("hasAnyRole('HOSTEL_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<List<HostelDto.AllotmentResponse>>> getAllAllotments() {
        log.info("Processing getAllAllotments endpoint request");
        List<HostelDto.AllotmentResponse> response = hostelService.getAllAllotments();
        log.info("Successfully processed getAllAllotments endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched all hostel allotments"));
    }

    // HOSTEL ADMIN VIEWS ALL ROOMS
    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('HOSTEL_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<List<HostelDto.RoomResponse>>> getAllRooms() {
        // Log trace at request entry point
        log.info("Processing getAllRooms endpoint request");
        
        List<HostelDto.RoomResponse> response = hostelService.getAllRooms();
        
        // Log trace at successful response point
        log.info("Successfully processed getAllRooms endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched all hostel rooms"));
    }

    // STUDENT CAN VIEW AVAILABLE ROOMS
    @GetMapping("/rooms/available")
    @PreAuthorize("hasAnyRole('HOSTEL_ADMIN','STUDENT','ADMIN')")
    public ResponseEntity<ApiResponse<List<HostelDto.RoomResponse>>> getAvailableRooms() {
        // Log trace at request entry point
        log.info("Processing getAvailableRooms endpoint request");
        
        List<HostelDto.RoomResponse> response = hostelService.getAvailableRooms();
        
        // Log trace at successful response point
        log.info("Successfully processed getAvailableRooms endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched available hostel rooms"));
    }

    // HOSTEL ADMIN ASSIGNS ROOM
    @PostMapping("/allotments")
    @PreAuthorize("hasAnyRole('HOSTEL_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<HostelDto.AllotmentResponse>> allotRoom(
            @Valid @RequestBody HostelDto.AllotmentRequest request) {
        // Log trace at request entry point
        log.info("Processing allotRoom endpoint request");

        HostelDto.AllotmentResponse response = hostelService.allotRoom(request);
        
        // Log trace at successful response point
        log.info("Successfully processed allotRoom endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Hostel room allotted successfully"));
    }

    // HOSTEL ADMIN VACATES ROOM
    @PutMapping("/allotments/{id}/vacate")
    @PreAuthorize("hasAnyRole('HOSTEL_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<HostelDto.AllotmentResponse>> vacateRoom(
            @PathVariable Long id) {
        // Log trace at request entry point using safe path variable
        log.info("Processing vacateRoom endpoint request for allotmentId: {}", id);

        HostelDto.AllotmentResponse response = hostelService.vacateRoom(id);
        
        // Log trace at successful response point
        log.info("Successfully processed vacateRoom endpoint request for allotmentId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(response, "Hostel room vacated successfully"));
    }

    // STUDENT CAN VIEW THEIR ALLOTMENT
    @GetMapping("/student/{studentId}/allotments")
    @PreAuthorize("hasAnyRole('HOSTEL_ADMIN','STUDENT','ADMIN')")
    public ResponseEntity<ApiResponse<List<HostelDto.AllotmentResponse>>> getStudentAllotments(
            @PathVariable Long studentId) {
        // Log trace at request entry point using safe path variable
        log.info("Processing getStudentAllotments endpoint request for studentId: {}", studentId);

        List<HostelDto.AllotmentResponse> response = hostelService.getStudentAllotments(studentId);
        
        // Log trace at successful response point
        log.info("Successfully processed getStudentAllotments endpoint request for studentId: {}", studentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Fetched student hostel allotments"));
    }
}