package com.campuscore.service;

import com.campuscore.dto.HostelDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.HostelAllotment;
import com.campuscore.entity.HostelApplication;
import com.campuscore.entity.HostelRoom;
import com.campuscore.entity.User;
import com.campuscore.exception.HostelException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.HostelAllotmentRepository;
import com.campuscore.repository.HostelApplicationRepository;
import com.campuscore.repository.HostelRoomRepository;
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
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HostelServiceTest {

    @Mock
    private HostelRoomRepository roomRepository;

    @Mock
    private HostelApplicationRepository hostelApplicationRepository;

    @Mock
    private HostelAllotmentRepository allotmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private HostelService hostelService;

    private User sampleStudent;
    private User sampleAdmin;
    private HostelRoom sampleRoom;
    private HostelApplication sampleApplication;
    private HostelAllotment sampleAllotment;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        sampleStudent = User.builder()
                .userId(1L)
                .name("Alice Smith")
                .email("alice@campuscore.com")
                .role(User.Role.STUDENT)
                .build();

        sampleAdmin = User.builder()
                .userId(2L)
                .name("Admin Hostels")
                .email("admin.hostel@campuscore.com")
                .role(User.Role.HOSTEL_ADMIN)
                .build();

        sampleRoom = HostelRoom.builder()
                .roomId(10L)
                .hostelBlock("Block A")
                .roomNumber("101")
                .capacity(2)
                .occupiedCount(0)
                .roomType(HostelRoom.RoomType.DOUBLE)
                .status(HostelRoom.RoomStatus.AVAILABLE)
                .build();

        sampleApplication = HostelApplication.builder()
                .applicationId(100L)
                .student(sampleStudent)
                .reason("Distance from campus")
                .roomType(HostelRoom.RoomType.DOUBLE)
                .applicationDate(LocalDate.now())
                .status(HostelApplication.ApplicationStatus.PENDING)
                .paymentStatus("PENDING")
                .hostelFee(0.0)
                .build();

        sampleAllotment = HostelAllotment.builder()
                .allotmentId(1000L)
                .student(sampleStudent)
                .room(sampleRoom)
                .academicYear("2025-2026")
                .checkinDate(LocalDate.now())
                .status(HostelAllotment.AllotmentStatus.ACTIVE)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityUser(User user) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    // ─────────────────────────────────────────────────────────
    // 1. CREATE ROOM TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void createRoom_Success() {
        HostelDto.RoomResponse request = HostelDto.RoomResponse.builder()
                .hostelBlock("Block B")
                .roomNumber("201")
                .capacity(2)
                .occupiedCount(0)
                .roomType("DOUBLE")
                .build();

        when(roomRepository.save(any(HostelRoom.class))).thenReturn(sampleRoom);

        HostelDto.RoomResponse response = hostelService.createRoom(request);

        assertNotNull(response);
        verify(roomRepository, times(1)).save(any(HostelRoom.class));
    }

    // ─────────────────────────────────────────────────────────
    // 2. APPLY FOR HOSTEL TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void applyForHostel_Success() {
        mockSecurityUser(sampleStudent);

        HostelDto.HostelApplicationRequest request = new HostelDto.HostelApplicationRequest();
        request.setReason("Need accommodation");
        request.setRoomType("DOUBLE");

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleStudent));
        when(hostelApplicationRepository.save(any(HostelApplication.class))).thenReturn(sampleApplication);

        HostelDto.HostelApplicationResponse response = hostelService.applyForHostel(1L, request);

        assertNotNull(response);
        assertEquals("PENDING", response.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void applyForHostel_ThrowsException_WhenInvalidRoomType() {
        mockSecurityUser(sampleStudent);

        HostelDto.HostelApplicationRequest request = new HostelDto.HostelApplicationRequest();
        request.setReason("Need accommodation");
        request.setRoomType("QUADRUPLE"); // Invalid type

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleStudent));

        assertThrows(HostelException.class, () -> hostelService.applyForHostel(1L, request));
    }

    // ─────────────────────────────────────────────────────────
    // 3. APPROVE & REJECT APPLICATION TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void approveApplication_Success() {
        when(hostelApplicationRepository.findById(100L)).thenReturn(Optional.of(sampleApplication));
        when(hostelApplicationRepository.save(any(HostelApplication.class))).thenReturn(sampleApplication);

        HostelDto.HostelApplicationResponse response = hostelService.approveApplication(100L, 2L);

        assertNotNull(response);
        assertEquals("APPROVED", response.getStatus());
        assertEquals(50000.0, response.getHostelFee()); // Double room pricing rule
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void rejectApplication_Success() {
        when(hostelApplicationRepository.findById(100L)).thenReturn(Optional.of(sampleApplication));
        when(allotmentRepository.findByStudentUserId(1L)).thenReturn(List.of());
        when(hostelApplicationRepository.save(any(HostelApplication.class))).thenReturn(sampleApplication);

        HostelDto.HostelApplicationResponse response = hostelService.rejectApplication(100L, 2L, "No capacity left");

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    // ─────────────────────────────────────────────────────────
    // 4. PAY HOSTEL FEE TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void payHostelFee_Success() {
        mockSecurityUser(sampleStudent);
        sampleApplication.setStatus(HostelApplication.ApplicationStatus.APPROVED);

        HostelDto.HostelPaymentRequest request = new HostelDto.HostelPaymentRequest();
        request.setPaymentMode("UPI");

        when(hostelApplicationRepository.findById(100L)).thenReturn(Optional.of(sampleApplication));
        when(hostelApplicationRepository.save(any(HostelApplication.class))).thenReturn(sampleApplication);

        HostelDto.HostelApplicationResponse response = hostelService.payHostelFee(100L, request);

        assertNotNull(response);
        assertEquals("PAID", response.getPaymentStatus());
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void payHostelFee_ThrowsException_WhenNotApproved() {
        mockSecurityUser(sampleStudent);
        sampleApplication.setStatus(HostelApplication.ApplicationStatus.PENDING);

        HostelDto.HostelPaymentRequest request = new HostelDto.HostelPaymentRequest();
        request.setPaymentMode("UPI");

        when(hostelApplicationRepository.findById(100L)).thenReturn(Optional.of(sampleApplication));

        assertThrows(HostelException.class, () -> hostelService.payHostelFee(100L, request));
    }

    // ─────────────────────────────────────────────────────────
    // 5. ALLOT ROOM & VACATE ROOM TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void allotRoom_Success() {
        sampleApplication.setStatus(HostelApplication.ApplicationStatus.APPROVED);
        sampleApplication.setPaymentStatus("PAID");

        HostelDto.AllotmentRequest request = new HostelDto.AllotmentRequest();
        request.setStudentId(1L);
        request.setRoomId(10L);
        request.setAcademicYear("2025-2026");
        request.setCheckinDate(LocalDate.now());

        when(hostelApplicationRepository.findByStudentUserIdAndStatus(1L, HostelApplication.ApplicationStatus.APPROVED))
                .thenReturn(Optional.of(sampleApplication));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleStudent));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(sampleRoom));
        when(allotmentRepository.findByStudentUserIdAndAcademicYear(1L, "2025-2026")).thenReturn(Optional.empty());
        when(allotmentRepository.save(any(HostelAllotment.class))).thenReturn(sampleAllotment);

        HostelDto.AllotmentResponse response = hostelService.allotRoom(request);

        assertNotNull(response);
        verify(roomRepository, times(1)).save(sampleRoom);
        verify(allotmentRepository, times(1)).save(any(HostelAllotment.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void allotRoom_ThrowsException_WhenFeeUnpaid() {
        sampleApplication.setStatus(HostelApplication.ApplicationStatus.APPROVED);
        sampleApplication.setPaymentStatus("PENDING");

        HostelDto.AllotmentRequest request = new HostelDto.AllotmentRequest();
        request.setStudentId(1L);
        request.setRoomId(10L);

        when(hostelApplicationRepository.findByStudentUserIdAndStatus(1L, HostelApplication.ApplicationStatus.APPROVED))
                .thenReturn(Optional.of(sampleApplication));

        assertThrows(HostelException.class, () -> hostelService.allotRoom(request));
    }

    @Test
    void vacateRoom_Success() {
        sampleRoom.setOccupiedCount(1);
        when(allotmentRepository.findById(1000L)).thenReturn(Optional.of(sampleAllotment));

        HostelDto.AllotmentResponse response = hostelService.vacateRoom(1000L);

        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
        assertEquals(0, sampleRoom.getOccupiedCount());
        assertEquals(HostelRoom.RoomStatus.AVAILABLE, sampleRoom.getStatus());
        verify(roomRepository, times(1)).save(sampleRoom);
    }

    // ─────────────────────────────────────────────────────────
    // 6. READ / QUERY TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void getStudentAllotments_Success() {
        mockSecurityUser(sampleStudent);
        when(allotmentRepository.findByStudentUserId(1L)).thenReturn(List.of(sampleAllotment));

        List<HostelDto.AllotmentResponse> allotments = hostelService.getStudentAllotments(1L);

        assertNotNull(allotments);
        assertEquals(1, allotments.size());
    }

    @Test
    void getAllRooms_Success() {
        when(roomRepository.findAll()).thenReturn(List.of(sampleRoom));

        List<HostelDto.RoomResponse> rooms = hostelService.getAllRooms();

        assertNotNull(rooms);
        assertEquals(1, rooms.size());
    }
}