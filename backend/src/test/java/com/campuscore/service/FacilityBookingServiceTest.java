package com.campuscore.service;

import com.campuscore.dto.HostelDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.FacilityBooking;
import com.campuscore.entity.User;
import com.campuscore.exception.FacilityBookingException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.FacilityBookingRepository;
import com.campuscore.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacilityBookingServiceTest {

    @Mock
    private FacilityBookingRepository bookingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private FacilityBookingService facilityBookingService;

    private User sampleUser;
    private User adminUser;
    private FacilityBooking sampleBooking;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        sampleUser = User.builder()
                .userId(1L)
                .name("John Doe")
                .email("john.doe@campuscore.com")
                .role(User.Role.STUDENT)
                .build();

        adminUser = User.builder()
                .userId(2L)
                .name("Admin User")
                .email("admin@campuscore.com")
                .role(User.Role.ADMIN)
                .build();

        sampleBooking = FacilityBooking.builder()
                .bookingId(100L)
                .facilityName("Auditorium")
                .bookedBy(sampleUser)
                .bookingDate(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .purpose("Seminar")
                .status(FacilityBooking.BookingStatus.REQUESTED)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(String email) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
    }

    // ─────────────────────────────────────────────────────────
    // 1. BOOK FACILITY TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void bookFacility_Success_AsStudent() {
        mockSecurityContext("john.doe@campuscore.com");

        HostelDto.BookingRequest request = new HostelDto.BookingRequest();
        request.setFacilityName("Auditorium");
        request.setBookingDate(LocalDate.now().plusDays(2));
        request.setStartTime(LocalTime.of(10, 0));
        request.setEndTime(LocalTime.of(12, 0));
        request.setPurpose("Seminar");

        when(userRepository.findByEmail("john.doe@campuscore.com")).thenReturn(Optional.of(sampleUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(bookingRepository.findByFacilityNameAndBookingDate(any(), any())).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(FacilityBooking.class))).thenReturn(sampleBooking);

        HostelDto.BookingResponse response = facilityBookingService.bookFacility(request, 1L);

        assertNotNull(response);
        assertEquals("Auditorium", response.getFacilityName());
        assertEquals("REQUESTED", response.getStatus());
        verify(bookingRepository, times(1)).save(any(FacilityBooking.class));
    }

    @Test
    void bookFacility_Success_AsAdmin_AutoApproved() {
        mockSecurityContext("admin@campuscore.com");

        HostelDto.BookingRequest request = new HostelDto.BookingRequest();
        request.setFacilityName("Auditorium");
        request.setBookingDate(LocalDate.now().plusDays(2));
        request.setStartTime(LocalTime.of(10, 0));
        request.setEndTime(LocalTime.of(12, 0));
        request.setPurpose("Official Event");

        FacilityBooking approvedBooking = FacilityBooking.builder()
                .bookingId(101L)
                .facilityName("Auditorium")
                .bookedBy(adminUser)
                .bookingDate(request.getBookingDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .purpose(request.getPurpose())
                .status(FacilityBooking.BookingStatus.APPROVED)
                .build();

        when(userRepository.findByEmail("admin@campuscore.com")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));
        when(bookingRepository.findByFacilityNameAndBookingDate(any(), any())).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(FacilityBooking.class))).thenReturn(approvedBooking);

        HostelDto.BookingResponse response = facilityBookingService.bookFacility(request, 2L);

        assertNotNull(response);
        assertEquals("APPROVED", response.getStatus());
    }

    @Test
    void bookFacility_ThrowsException_WhenAccessingOtherUserAccount() {
        mockSecurityContext("john.doe@campuscore.com");

        HostelDto.BookingRequest request = new HostelDto.BookingRequest();

        when(userRepository.findByEmail("john.doe@campuscore.com")).thenReturn(Optional.of(sampleUser));

        // Authenticated user ID is 1, but target ID passed is 2
        assertThrows(FacilityBookingException.class, () -> facilityBookingService.bookFacility(request, 2L));
    }

    @Test
    void bookFacility_ThrowsException_WhenSlotOverlaps() {
        mockSecurityContext("john.doe@campuscore.com");

        HostelDto.BookingRequest request = new HostelDto.BookingRequest();
        request.setFacilityName("Auditorium");
        request.setBookingDate(LocalDate.now().plusDays(2));
        request.setStartTime(LocalTime.of(11, 0)); // Overlaps existing 10:00 - 12:00
        request.setEndTime(LocalTime.of(13, 0));

        when(userRepository.findByEmail("john.doe@campuscore.com")).thenReturn(Optional.of(sampleUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(bookingRepository.findByFacilityNameAndBookingDate(any(), any())).thenReturn(List.of(sampleBooking));

        assertThrows(FacilityBookingException.class, () -> facilityBookingService.bookFacility(request, 1L));
    }

    // ─────────────────────────────────────────────────────────
    // 2. UPDATE STATUS TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void updateStatus_Success() {
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(sampleBooking));
        when(bookingRepository.save(any(FacilityBooking.class))).thenReturn(sampleBooking);

        HostelDto.BookingResponse response = facilityBookingService.updateStatus(100L, "APPROVED");

        assertNotNull(response);
        assertEquals("APPROVED", response.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void updateStatus_ThrowsException_WhenBookingNotFound() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> facilityBookingService.updateStatus(999L, "APPROVED"));
    }

    // ─────────────────────────────────────────────────────────
    // 3. READ / QUERY TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getUserBookings_Success() {
        mockSecurityContext("john.doe@campuscore.com");

        when(userRepository.findByEmail("john.doe@campuscore.com")).thenReturn(Optional.of(sampleUser));
        when(bookingRepository.findByBookedByUserIdOrderByBookingDateDesc(1L)).thenReturn(List.of(sampleBooking));

        List<HostelDto.BookingResponse> response = facilityBookingService.getUserBookings(1L);

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("Auditorium", response.get(0).getFacilityName());
    }

    @Test
    void getAllBookings_Success() {
        when(bookingRepository.findAll()).thenReturn(List.of(sampleBooking));

        List<HostelDto.BookingResponse> response = facilityBookingService.getAllBookings();

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(bookingRepository, times(1)).findAll();
    }
}