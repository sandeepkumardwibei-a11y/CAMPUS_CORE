package com.campuscore.service;

import com.campuscore.dto.HostelDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.HostelAllotment;
import com.campuscore.entity.HostelApplication;
import com.campuscore.entity.HostelRoom;
import com.campuscore.entity.Notification.NotificationCategory;
import com.campuscore.entity.User;
import com.campuscore.exception.HostelException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.HostelAllotmentRepository;
import com.campuscore.repository.HostelApplicationRepository;
import com.campuscore.repository.HostelRoomRepository;
import com.campuscore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HostelService {

    private final HostelRoomRepository roomRepository;
    private final HostelApplicationRepository hostelApplicationRepository;
    private final HostelAllotmentRepository allotmentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     *  SECURITY GUARD: Only permits access if the active session matches the student resource id,
     * or if the logged-in user is a system Admin / Hostel Admin.
     */
    private void verifyStudentDataOwnership(Long studentUserId) {
        log.debug("Verifying student data ownership for studentUserId: {}", studentUserId);
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername;

        if (principal instanceof UserDetails) {
            currentUsername = ((UserDetails) principal).getUsername();
        } else {
            currentUsername = principal.toString();
        }

        User currentUser = userRepository.findByEmail(currentUsername)
                .orElseThrow(() -> {
                    log.error("Security execution context invalid. Username: {} not found.", currentUsername);
                    return new HostelException("Access Denied: Invalid security execution context.");
                });

        //  FIXED PATHWAY LOOKUP: Allow the student owner, system ADMIN, or HOSTEL_ADMIN to pass through smoothly
        if (!currentUser.getUserId().equals(studentUserId) && 
            currentUser.getRole() != User.Role.ADMIN && 
            currentUser.getRole() != User.Role.HOSTEL_ADMIN) {
            log.warn("Access Denied: User {} (Role: {}) attempted unauthorized access to studentUserId: {}", 
                    currentUsername, currentUser.getRole(), studentUserId);
            throw new HostelException("Access Denied: You are not authorized to access or modify this student's hostel record.");
        }
        log.debug("Data ownership successfully verified for user: {}", currentUsername);
    }

    @Transactional
    public HostelDto.RoomResponse createRoom(HostelDto.RoomResponse request) {
        log.info("Creating a new hostel room. Block: {}, Room Number: {}", request.getHostelBlock(), request.getRoomNumber());

        // VALIDATION: block and room number are mandatory and must not be blank.
        String block = request.getHostelBlock() != null ? request.getHostelBlock().trim() : "";
        String roomNo = request.getRoomNumber() != null ? request.getRoomNumber().trim() : "";
        if (block.isEmpty()) {
            throw new HostelException("Hostel block is required — please enter a block name (e.g. A-Block).");
        }
        if (roomNo.isEmpty()) {
            throw new HostelException("Room number is required — please enter a room number (e.g. 101).");
        }
        if (request.getRoomType() == null || request.getRoomType().isBlank()) {
            throw new HostelException("Room type is required (SINGLE, DOUBLE, TRIPLE).");
        }

        // VALIDATION: reject duplicates with a clear, friendly message.
        if (roomRepository.existsByHostelBlockIgnoreCaseAndRoomNumberIgnoreCase(block, roomNo)) {
            throw new HostelException("Room " + roomNo + " already exists in " + block + ". Please use a different block or room number.");
        }

        int finalCapacity = request.getCapacity() != null ? request.getCapacity() : 2;
        int finalOccupiedCount = request.getOccupiedCount() != null ? request.getOccupiedCount() : 0;

        HostelRoom.RoomStatus finalStatus = (finalOccupiedCount >= finalCapacity)
                ? HostelRoom.RoomStatus.OCCUPIED
                : HostelRoom.RoomStatus.AVAILABLE;

        HostelRoom.RoomType parsedType;
        try {
            parsedType = HostelRoom.RoomType.valueOf(request.getRoomType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new HostelException("Invalid room type '" + request.getRoomType() + "'. Valid types: SINGLE, DOUBLE, TRIPLE.");
        }

        HostelRoom room = HostelRoom.builder()
                .hostelBlock(block)
                .roomNumber(roomNo)
                .capacity(finalCapacity)
                .occupiedCount(finalOccupiedCount)
                .roomType(parsedType)
                .status(finalStatus)
                .build();

        roomRepository.save(room);
        log.info("Successfully created room with ID: {} and Status: {}", room.getRoomId(), room.getStatus());
        return toRoomResponse(room);
    }

    @Transactional
    public HostelDto.HostelApplicationResponse applyForHostel(
            Long studentId,
            HostelDto.HostelApplicationRequest request) {

        log.info("Processing hostel application submission for studentId: {}", studentId);
        verifyStudentDataOwnership(studentId);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> {
                    log.error("User not found with studentId: {}", studentId);
                    return new ResourceNotFoundException("User", "id", studentId);
                });

        HostelRoom.RoomType selectedType;
        try {
            selectedType = HostelRoom.RoomType.valueOf(request.getRoomType().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Failed to parse room type: {}", request.getRoomType());
            throw new HostelException("Invalid room configuration selected. Valid types: SINGLE, DOUBLE, TRIPLE.");
        }

        // RULE: a student cannot stack a second PENDING application on top of one still
        // awaiting the hostel admin's decision. The UI shows an "under process" popup,
        // and this guard enforces the same rule server-side.
        boolean hasPending = hostelApplicationRepository
                .findByStudentUserIdAndStatus(studentId, HostelApplication.ApplicationStatus.PENDING)
                .isPresent();
        if (hasPending) {
            log.warn("Apply blocked: student {} already has a PENDING hostel application", studentId);
            throw new HostelException("Your previous hostel application is still under process. Please wait until the hostel admin confirms it.");
        }

        // Default the study year to 1 (1st year) when the caller doesn't specify it.
        Integer studyYear = request.getStudyYear() != null ? request.getStudyYear() : 1;

        HostelApplication application = HostelApplication.builder()
                .student(student)
                .reason(request.getReason())
                .roomType(selectedType)
                .studyYear(studyYear)
                .applicationDate(LocalDate.now())
                .status(HostelApplication.ApplicationStatus.PENDING)
                .paymentStatus("PENDING")
                .hostelFee(0.0)
                .build();

        hostelApplicationRepository.save(application);

        // 🔔 AUTOMATIC NOTIFICATION: Inform the student that their dynamic request registration was logged safely
        String applicationMessage = String.format(
                "Hostel Application Submitted: Your request for a %s room layout option has been safely captured on %s and is currently under pending evaluation.",
                application.getRoomType().name(),
                application.getApplicationDate().toString()
        );
        eventPublisher.publishEvent(new NotificationDto.Event(student, applicationMessage, NotificationCategory.HOSTEL));

        log.info("Hostel application successfully submitted. Application ID: {}", application.getApplicationId());
        return toApplicationResponse(application);
    }

    @Transactional
    public HostelDto.HostelApplicationResponse approveApplication(
            Long applicationId,
            Long hostelAdminId) {

        log.info("Approving hostel application ID: {} by adminId: {}", applicationId, hostelAdminId);
        HostelApplication application = hostelApplicationRepository.findById(applicationId)
                .orElseThrow(() -> {
                    log.error("Hostel application ID: {} not found", applicationId);
                    return new ResourceNotFoundException("HostelApplication", "id", applicationId);
                });

        if (application.getStatus() == HostelApplication.ApplicationStatus.APPROVED) {
            throw new HostelException("Application is already approved");
        }
        if (application.getStatus() == HostelApplication.ApplicationStatus.REJECTED) {
            throw new HostelException("Rejected application cannot be approved");
        }

        //  PRICING ENGINE MAPPER
        // Base full fee by room type.
        double fullFee;
        switch (application.getRoomType()) {
            case SINGLE -> fullFee = 75000.0;
            case DOUBLE -> fullFee = 50000.0;
            case TRIPLE -> fullFee = 40000.0;
            default -> {
                log.error("Unhandled pricing rule for Room Type: {}", application.getRoomType());
                throw new HostelException("Unhandled pricing rule for Room Type: " + application.getRoomType());
            }
        }

        // RE-APPLY PRICING RULE:
        // If the student is already staying (has an ACTIVE allotment) and is re-applying
        // for the SAME year of study they're currently in, the re-application is FREE
        // (fee 0, auto-marked PAID). Re-applying for any OTHER year is charged the full fee.
        Long studentId = application.getStudent().getUserId();
        boolean isStayingNow = allotmentRepository.findByStudentUserId(studentId).stream()
                .anyMatch(a -> a.getStatus() == HostelAllotment.AllotmentStatus.ACTIVE);

        boolean freeReapply = false;
        if (isStayingNow) {
            // The year they're currently staying for = studyYear of their latest APPROVED
            // application other than this one.
            Integer stayingYear = hostelApplicationRepository.findByStudentUserId(studentId).stream()
                    .filter(a -> !a.getApplicationId().equals(application.getApplicationId()))
                    .filter(a -> a.getStatus() == HostelApplication.ApplicationStatus.APPROVED)
                    .map(HostelApplication::getStudyYear)
                    .filter(java.util.Objects::nonNull)
                    .reduce((first, second) -> second) // last one
                    .orElse(null);

            Integer newYear = application.getStudyYear();
            if (stayingYear != null && newYear != null && stayingYear.equals(newYear)) {
                freeReapply = true;
            }
        }

        if (freeReapply) {
            application.setHostelFee(0.0);
            application.setPaymentStatus("PAID"); // nothing to collect — mark settled
            log.info("Re-apply for same study year {} while staying — fee waived for application ID: {}",
                    application.getStudyYear(), applicationId);
        } else {
            application.setHostelFee(fullFee);
            application.setPaymentStatus("PENDING");
        }

        application.setStatus(HostelApplication.ApplicationStatus.APPROVED);
        application.setHostelAdminId(hostelAdminId);

        hostelApplicationRepository.save(application);

        // 🔔 AUTOMATIC NOTIFICATION: Inform the student that their registration has been accepted
        String approvalMessage = String.format(
                "Hostel Application Approved! Your registration for a %s room has been approved by the administration panel. Outstanding Fee Liability: %.2f.",
                application.getRoomType().name(),
                application.getHostelFee()
        );
        eventPublisher.publishEvent(new NotificationDto.Event(application.getStudent(), approvalMessage, NotificationCategory.HOSTEL));

        log.info("Application ID: {} successfully approved. Fee set to: {}", applicationId, application.getHostelFee());
        return toApplicationResponse(application);
    }

    /**
     *  CONSTRUCTED ACTION: Strictly handles transactions inside the hostel module 
     * while accepting dynamic payment modes (CASH, ONLINE, CARD, NEFT).
     */
    @Transactional
    public HostelDto.HostelApplicationResponse payHostelFee(Long applicationId, HostelDto.HostelPaymentRequest request) {
        log.info("Processing hostel fee payment for application ID: {}", applicationId);
        HostelApplication application = hostelApplicationRepository.findById(applicationId)
                .orElseThrow(() -> {
                    log.error("Hostel application ID: {} not found for payment processing", applicationId);
                    return new ResourceNotFoundException("HostelApplication", "id", applicationId);
                });

        verifyStudentDataOwnership(application.getStudent().getUserId());

        if (application.getStatus() != HostelApplication.ApplicationStatus.APPROVED) {
            log.warn("Payment rejected. Application ID: {} status is {}", applicationId, application.getStatus());
            throw new HostelException("Payments can only be accepted on approved registrations.");
        }
        if ("PAID".equalsIgnoreCase(application.getPaymentStatus())) {
            log.warn("Payment rejected. Application ID: {} is already PAID", applicationId);
            throw new HostelException("Payment was already processed for this academic cycle.");
        }

        // Validate incoming payment configuration
        String mode = request.getPaymentMode().toUpperCase();
        java.util.List<String> validModes = java.util.List.of("CASH", "ONLINE", "CARD", "NEFT", "UPI", "NETBANKING", "NET_BANKING", "BANK_TRANSFER", "DD");
        if (!validModes.contains(mode)) {
            log.error("Invalid payment mode attempted: {}", request.getPaymentMode());
            throw new HostelException("Invalid payment mode selected. Valid options: UPI, ONLINE, NETBANKING, CASH.");
        }

        application.setPaymentStatus("PAID");
        hostelApplicationRepository.save(application);

        // 🔔 AUTOMATIC NOTIFICATION: Alert the student regarding their finalized fee payment ledger status
        String paymentMessage = String.format(
                "Hostel Payment Verified: Your fee payment of %.2f via mode '%s' has been successfully verified. You are now fully eligible for room allocation updates.",
                application.getHostelFee(),
                mode
        );
        eventPublisher.publishEvent(new NotificationDto.Event(application.getStudent(), paymentMessage, NotificationCategory.HOSTEL));

        log.info("Payment successfully recorded via mode: {} for application ID: {}", mode, applicationId);
        return toApplicationResponse(application);
    }

    @Transactional
    public HostelDto.AllotmentResponse allotRoom(HostelDto.AllotmentRequest request) {
        log.info("Initiating room allotment process for student ID: {} to room ID: {}", request.getStudentId(), request.getRoomId());
        HostelApplication application = hostelApplicationRepository
                .findByStudentUserIdAndStatus(request.getStudentId(), HostelApplication.ApplicationStatus.APPROVED)
                .orElseThrow(() -> {
                    log.warn("Allotment failed: Approved hostel application not found for student ID: {}", request.getStudentId());
                    return new HostelException("Student hostel application is not approved");
                });

        if (!"PAID".equalsIgnoreCase(application.getPaymentStatus())) {
            log.warn("Allotment failed: Payment status is {} for student ID: {}", application.getPaymentStatus(), request.getStudentId());
            throw new HostelException("Allocation rejected! Approved student must first settle fees directly inside the hostel module.");
        }

        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> {
                    log.error("Student user records missing for user ID: {}", request.getStudentId());
                    return new ResourceNotFoundException("User", "id", request.getStudentId());
                });

        HostelRoom room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> {
                    log.error("Hostel room ID: {} not found during allocation", request.getRoomId());
                    return new ResourceNotFoundException("HostelRoom", "id", request.getRoomId());
                });

        if (room.getRoomType() != application.getRoomType()) {
            log.warn("Allotment failed: Room layout category mismatch. Requested: {}, Room Type: {}", application.getRoomType(), room.getRoomType());
            throw new HostelException("Mismatched allocation assignment category: Chosen room type does not match requested layout pattern.");
        }

        // RULE: a student may hold only ONE active room at a time. If they already have
        // an ACTIVE allotment (any room, any year), the admin must VACATE it before
        // assigning a new one — we never double-assign.
        boolean alreadyStaying = allotmentRepository.findByStudentUserId(request.getStudentId()).stream()
                .anyMatch(a -> a.getStatus() == HostelAllotment.AllotmentStatus.ACTIVE);
        if (alreadyStaying) {
            log.warn("Allotment failed: student ID: {} already has an ACTIVE allotment", request.getStudentId());
            throw new HostelException("This student is already staying in a room. Please vacate their current room before assigning a new one.");
        }

        if (allotmentRepository.findByStudentUserIdAndAcademicYear(request.getStudentId(), request.getAcademicYear()).isPresent()) {
            log.warn("Allotment failed: Active room allotment exists for student ID: {} in academic year: {}", request.getStudentId(), request.getAcademicYear());
            throw new HostelException("Student already has a room allotted for this academic year");
        }
        if (room.getStatus() != HostelRoom.RoomStatus.AVAILABLE) {
            log.warn("Allotment failed: Target room ID: {} status is {}", request.getRoomId(), room.getStatus());
            throw new HostelException("Room is not available for allotment");
        }
        if (room.getOccupiedCount() >= room.getCapacity()) {
            log.warn("Allotment failed: Room ID: {} is at full capacity ({}/{})", request.getRoomId(), room.getOccupiedCount(), room.getCapacity());
            throw new HostelException("Room is already full");
        }

        HostelAllotment allotment = HostelAllotment.builder()
                .student(student)
                .room(room)
                .academicYear(request.getAcademicYear())
                .checkinDate(request.getCheckinDate())
                .status(HostelAllotment.AllotmentStatus.ACTIVE)
                .build();

        allotmentRepository.save(allotment);

        room.setOccupiedCount(room.getOccupiedCount() + 1);
        if (room.getOccupiedCount().equals(room.getCapacity())) {
            room.setStatus(HostelRoom.RoomStatus.OCCUPIED);
        }
        roomRepository.save(room);
        log.info("Room ID: {} metrics successfully updated. New occupied count: {}", room.getRoomId(), room.getOccupiedCount());

        String messageText = String.format(
                "Hostel Room Allocated! You have been successfully allocated Room No: %s in Block: %s for the Academic Year %s.",
                room.getRoomNumber(), room.getHostelBlock(), allotment.getAcademicYear());

        eventPublisher.publishEvent(new NotificationDto.Event(student, messageText, NotificationCategory.HOSTEL));
        log.info("Transactional routing complete. Allotment event published successfully for allotment ID: {}", allotment.getAllotmentId());

        return toAllotmentResponse(allotment);
    }

    @Transactional(readOnly = true)
    public List<HostelDto.AllotmentResponse> getStudentAllotments(Long studentId) {
        log.debug("Fetching hostel allotments for studentId: {}", studentId);
        verifyStudentDataOwnership(studentId);

        return allotmentRepository.findByStudentUserId(studentId)
                .stream()
                .map(this::toAllotmentResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public HostelDto.HostelApplicationResponse rejectApplication(Long applicationId, Long hostelAdminId, String reason) {
        log.info("Rejecting hostel application ID: {} by adminId: {}", applicationId, hostelAdminId);
        HostelApplication application = hostelApplicationRepository.findById(applicationId)
                .orElseThrow(() -> {
                    log.error("Hostel application ID: {} not found for rejection workflow", applicationId);
                    return new ResourceNotFoundException("HostelApplication", "id", applicationId);
                });

        boolean hasActiveAllotment = allotmentRepository.findByStudentUserId(application.getStudent().getUserId())
                .stream()
                .anyMatch(a -> a.getStatus() == HostelAllotment.AllotmentStatus.ACTIVE);

        if (hasActiveAllotment) {
            log.warn("Rejection failed: Student associated with application ID: {} already has an active allotment", applicationId);
            throw new HostelException("Cannot reject application. Room already allotted to student");
        }
        if (application.getStatus() == HostelApplication.ApplicationStatus.REJECTED) {
            log.warn("Application ID: {} is already marked as REJECTED", applicationId);
            throw new HostelException("Application is already rejected");
        }

        application.setStatus(HostelApplication.ApplicationStatus.REJECTED);
        application.setHostelAdminId(hostelAdminId);
        application.setRejectionReason(reason != null && !reason.isBlank() ? reason : "No reason provided.");
        hostelApplicationRepository.save(application);

        // 🔔 AUTOMATIC NOTIFICATION: Alert the student if their application structure gets rejected
        String rejectionMessage = String.format(
                "Hostel Application Update: Your request for the %s room layout configuration cycle has been turned down by the housing board. Reason: %s",
                application.getRoomType().name(),
                application.getRejectionReason()
        );
        eventPublisher.publishEvent(new NotificationDto.Event(application.getStudent(), rejectionMessage, NotificationCategory.HOSTEL));

        log.info("Application ID: {} has been rejected successfully", applicationId);
        return toApplicationResponse(application);
    }

    @Transactional(readOnly = true)
    public List<HostelDto.HostelApplicationResponse> getAllApplications() {
        log.debug("Fetching all hostel applications for the admin dashboard");
        return hostelApplicationRepository.findAll().stream()
                .map(this::toApplicationResponse)
                .collect(Collectors.toList());
    }

    // A student's OWN applications (or admin/hostel-admin viewing a specific student).
    @Transactional(readOnly = true)
    public List<HostelDto.HostelApplicationResponse> getStudentApplications(Long studentId) {
        log.debug("Fetching hostel applications for studentId: {}", studentId);
        verifyStudentDataOwnership(studentId);
        return hostelApplicationRepository.findByStudentUserId(studentId).stream()
                .map(this::toApplicationResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HostelDto.AllotmentResponse> getAllAllotments() {
        log.debug("Fetching all hostel allotments for the admin dashboard");
        return allotmentRepository.findAll().stream()
                .map(this::toAllotmentResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HostelDto.RoomResponse> getAllRooms() {
        log.debug("Fetching inventory listing for all managed rooms");
        return roomRepository.findAll().stream().map(this::toRoomResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HostelDto.RoomResponse> getAvailableRooms() {
        log.debug("Fetching all rooms matching status: AVAILABLE");
        return roomRepository.findByStatus(HostelRoom.RoomStatus.AVAILABLE).stream().map(this::toRoomResponse).collect(Collectors.toList());
    }

    @Transactional
    public HostelDto.AllotmentResponse vacateRoom(Long allotmentId) {
        log.info("Processing checkout/vacate request for allotment ID: {}", allotmentId);
        HostelAllotment allotment = allotmentRepository.findById(allotmentId)
                .orElseThrow(() -> {
                    log.error("Hostel allotment ID: {} not found", allotmentId);
                    return new ResourceNotFoundException("HostelAllotment", "id", allotmentId);
                });

        if (allotment.getStatus() != HostelAllotment.AllotmentStatus.ACTIVE) {
            log.warn("Vacate failed: Allotment ID: {} status is already inactive ({})", allotmentId, allotment.getStatus());
            throw new HostelException("Allotment is already inactive");
        }

        allotment.setStatus(HostelAllotment.AllotmentStatus.COMPLETED);
        allotment.setCheckoutDate(LocalDate.now());
        allotmentRepository.save(allotment);

        HostelRoom room = allotment.getRoom();
        room.setOccupiedCount(Math.max(0, room.getOccupiedCount() - 1));

        if (room.getOccupiedCount() < room.getCapacity()) {
            room.setStatus(HostelRoom.RoomStatus.AVAILABLE);
        }
        roomRepository.save(room);
        log.info("Allotment ID: {} deactivated. Room ID: {} occupied counter updated to {}", allotmentId, room.getRoomId(), room.getOccupiedCount());

        return toAllotmentResponse(allotment);
    }

    private HostelDto.RoomResponse toRoomResponse(HostelRoom r) {
        // Look up who is currently staying in this room (ACTIVE allotments) and list their names.
        java.util.List<String> occupantNames = allotmentRepository
                .findByRoomRoomIdAndStatus(r.getRoomId(), HostelAllotment.AllotmentStatus.ACTIVE)
                .stream()
                .map(a -> a.getStudent().getName())
                .collect(Collectors.toList());

        return HostelDto.RoomResponse.builder()
                .roomId(r.getRoomId())
                .hostelBlock(r.getHostelBlock())
                .roomNumber(r.getRoomNumber())
                .capacity(r.getCapacity())
                .occupiedCount(r.getOccupiedCount())
                .availableBeds(r.getCapacity() - r.getOccupiedCount())
                .roomType(r.getRoomType().name())
                .status(r.getStatus().name())
                .occupants(occupantNames)
                .build();
    }

    private HostelDto.AllotmentResponse toAllotmentResponse(HostelAllotment a) {
        return HostelDto.AllotmentResponse.builder()
                .allotmentId(a.getAllotmentId())
                .studentId(a.getStudent().getUserId())
                .studentName(a.getStudent().getName())
                .roomId(a.getRoom().getRoomId())
                .hostelBlock(a.getRoom().getHostelBlock())
                .roomNumber(a.getRoom().getRoomNumber())
                .academicYear(a.getAcademicYear())
                .checkinDate(a.getCheckinDate())
                .checkoutDate(a.getCheckoutDate())
                .status(a.getStatus().name())
                .build();
    }

    private HostelDto.HostelApplicationResponse toApplicationResponse(HostelApplication a) {
        return HostelDto.HostelApplicationResponse.builder()
                .applicationId(a.getApplicationId())
                .studentId(a.getStudent().getUserId())
                .studentName(a.getStudent().getName())
                .reason(a.getReason())
                .roomType(a.getRoomType() != null ? a.getRoomType().name() : null)
                .studyYear(a.getStudyYear())
                .applicationDate(a.getApplicationDate())
                .status(a.getStatus().name())
                .hostelFee(a.getHostelFee())
                .paymentStatus(a.getPaymentStatus())
                .paymentMode(a.getPaymentStatus().equals("PAID") ? "VERIFIED" : null)
                .rejectionReason(a.getRejectionReason())
                .build();
    }
}