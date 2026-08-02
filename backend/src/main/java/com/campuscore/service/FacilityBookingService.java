package com.campuscore.service;

import com.campuscore.dto.HostelDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.FacilityBooking;
import com.campuscore.entity.Notification.NotificationCategory;
import com.campuscore.entity.User;
import com.campuscore.exception.FacilityBookingException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.FacilityBookingRepository;
import com.campuscore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityBookingService {

    private final FacilityBookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * SECURITY BOUNDARY: Validates that the authenticated user matches the target userId parameters.
     */
    private void verifyOwnership(Long targetedUserId) {
        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> {
                    log.error("Security Fault: Authenticated user identity '{}' context missing.", authenticatedEmail);
                    return new ResourceNotFoundException("User", "email", authenticatedEmail);
                });

        if (!currentUser.getUserId().equals(targetedUserId)) {
            log.error("Access Denied: Authenticated User ID {} attempted accessing or modifying records reserved for Target User ID {}.", 
                    currentUser.getUserId(), targetedUserId);
            throw new FacilityBookingException("Access Denied: You are only allowed to view or request actions for your own account.");
        }
    }

    private static final java.time.LocalTime DAY_START = java.time.LocalTime.of(8, 0);
    private static final java.time.LocalTime DAY_END = java.time.LocalTime.of(16, 0);

    @Transactional
    public HostelDto.BookingResponse bookFacility(HostelDto.BookingRequest request, Long userId) {
        log.info("Entering bookFacility sequence for facilityName: {} and userId: {}", request.getFacilityName(), userId);

        // 🔐 Enforce explicit identity validation rule
        verifyOwnership(userId);

        // RULE: Start time must be strictly before end time.
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            log.warn("Booking creation rejected: end time ({}) is not after start time ({}).", request.getEndTime(), request.getStartTime());
            throw new FacilityBookingException("Validation Failed: Start time (" + request.getStartTime() +
                    ") must be strictly before the end time (" + request.getEndTime() + ").");
        }

        // RULE: Booking must fall within college operating hours (08:00–16:00).
        if (request.getStartTime().isBefore(DAY_START) || request.getEndTime().isAfter(DAY_END)) {
            log.warn("Booking creation rejected: outside college hours. Start: {}, End: {}", request.getStartTime(), request.getEndTime());
            throw new FacilityBookingException("Validation Failed: Bookings must fall within college hours (08:00–16:00).");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Check overlaps
        List<FacilityBooking> existing = bookingRepository.findByFacilityNameAndBookingDate(
                request.getFacilityName(), request.getBookingDate());

        for (FacilityBooking b : existing) {
            if (b.getStatus() == FacilityBooking.BookingStatus.APPROVED || b.getStatus() == FacilityBooking.BookingStatus.REQUESTED) {
                if (request.getStartTime().isBefore(b.getEndTime()) && request.getEndTime().isAfter(b.getStartTime())) {
                    log.error("Booking Error: The facility '{}' is already reserved during the requested period.", request.getFacilityName());
                    throw new FacilityBookingException("Facility is already booked/requested for the selected time slot");
                }
            }
        }

        // 👑 Automated System Rule: Admin accounts bypass validation approval states
        FacilityBooking.BookingStatus initialStatus = (user.getRole() == User.Role.ADMIN) 
                ? FacilityBooking.BookingStatus.APPROVED 
                : FacilityBooking.BookingStatus.REQUESTED;

        FacilityBooking booking = FacilityBooking.builder()
                .facilityName(request.getFacilityName())
                .bookedBy(user)
                .bookingDate(request.getBookingDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .purpose(request.getPurpose())
                .status(initialStatus)
                .build();

        bookingRepository.save(booking);
        
        log.info("Successfully created and saved booking request with ID: {} under initialization state: {}", booking.getBookingId(), initialStatus.name());
        return toBookingResponse(booking);
    }

    @Transactional
    public HostelDto.BookingResponse updateStatus(Long bookingId, String status) {
        log.info("Entering updateStatus sequence for bookingId: {} moving to status: {}", bookingId, status);

        FacilityBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityBooking", "id", bookingId));

        FacilityBooking.BookingStatus newStatus = FacilityBooking.BookingStatus.valueOf(status.toUpperCase());

        // RULE: No two bookings for the same facility can both be APPROVED for an
        // overlapping time window — check against every other already-APPROVED booking.
        if (newStatus == FacilityBooking.BookingStatus.APPROVED) {
            List<FacilityBooking> sameDayBookings = bookingRepository.findByFacilityNameAndBookingDate(
                    booking.getFacilityName(), booking.getBookingDate());
            for (FacilityBooking other : sameDayBookings) {
                if (other.getBookingId().equals(booking.getBookingId())) continue;
                if (other.getStatus() != FacilityBooking.BookingStatus.APPROVED) continue;
                boolean overlaps = booking.getStartTime().isBefore(other.getEndTime())
                        && booking.getEndTime().isAfter(other.getStartTime());
                if (overlaps) {
                    log.warn("Approval rejected: booking ID {} overlaps with already-approved booking ID {}.", bookingId, other.getBookingId());
                    throw new FacilityBookingException("Scheduling Conflict: '" + booking.getFacilityName() +
                            "' already has an approved booking on " + booking.getBookingDate() +
                            " between " + other.getStartTime() + " and " + other.getEndTime() +
                            ". Two bookings for the same facility cannot both be approved for overlapping times.");
                }
            }
        }

        booking.setStatus(newStatus);
        FacilityBooking savedBooking = bookingRepository.save(booking);

        String statusMessage = String.format(
            "Facility Booking Update: Your booking request for the '%s' facility on %s (%s to %s) has been %s by the administration panel.",
            savedBooking.getFacilityName(),
            savedBooking.getBookingDate().toString(),
            savedBooking.getStartTime().toString(),
            savedBooking.getEndTime().toString(),
            newStatus.name()
        );

        eventPublisher.publishEvent(new NotificationDto.Event(
            savedBooking.getBookedBy(),
            statusMessage,
            NotificationCategory.HOSTEL
        ));

        log.info("Successfully updated status to {} and triggered notification event for bookingId: {}", newStatus.name(), bookingId);
        return toBookingResponse(savedBooking);
    }

    @Transactional(readOnly = true)
    public List<HostelDto.BookingResponse> getUserBookings(Long userId) {
        log.info("Fetching facility bookings matrix collection for userId: {}", userId);

        // 🔐 Enforce identity verification lookup rule
        verifyOwnership(userId);

        return bookingRepository.findByBookedByUserIdOrderByBookingDateDesc(userId).stream()
                .map(this::toBookingResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HostelDto.BookingResponse> getAllBookings() {
        log.info("Fetching global system administrative index sheets for all facility bookings");
        return bookingRepository.findAll().stream()
                .map(this::toBookingResponse).collect(Collectors.toList());
    }

    private HostelDto.BookingResponse toBookingResponse(FacilityBooking b) {
        return HostelDto.BookingResponse.builder()
                .bookingId(b.getBookingId())
                .facilityName(b.getFacilityName())
                .bookedById(b.getBookedBy().getUserId())
                .bookedByName(b.getBookedBy().getName())
                .bookingDate(b.getBookingDate())
                .startTime(b.getStartTime())
                .endTime(b.getEndTime())
                .purpose(b.getPurpose())
                .status(b.getStatus().name())
                .build();
    }
}