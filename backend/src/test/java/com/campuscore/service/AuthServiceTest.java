package com.campuscore.service;

import com.campuscore.dto.AuthRequest;
import com.campuscore.dto.AuthResponse;
import com.campuscore.dto.NotificationDto;
import com.campuscore.dto.RegisterRequest;
import com.campuscore.entity.AuditLog;
import com.campuscore.entity.User;
import com.campuscore.exception.AuthException;
import com.campuscore.exception.DuplicateResourceException;
import com.campuscore.repository.AuditLogRepository;
import com.campuscore.repository.DepartmentRepository;
import com.campuscore.repository.UserRepository;
import com.campuscore.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder; // 🎯 FIXED: Added missing import

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private RegisterRequest registerRequest;
    private AuthRequest authRequest;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .userId(1L)
                .name("John Doe")
                .email("john.doe@example.com")
                .password("encoded_password")
                .phone("1234567890")
                .role(User.Role.APPLICANT)
                .status(User.UserStatus.ACTIVE)
                .build();

        registerRequest = new RegisterRequest();
        registerRequest.setName("John Doe");
        registerRequest.setEmail("john.doe@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setPhone("1234567890");
        registerRequest.setRole(User.Role.APPLICANT);

        authRequest = new AuthRequest();
        authRequest.setEmail("john.doe@example.com");
        authRequest.setPassword("password123");
    }

    // ─────────────────────────────────────────────────────────
    // 1. REGISTER TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void register_Success_ApplicantRole() {
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setUserId(1L);
            return u;
        });

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals("John Doe", response.getName());
        assertEquals("john.doe@example.com", response.getEmail());
        assertEquals("APPLICANT", response.getRole());

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void register_ThrowsException_WhenEmailAlreadyRegistered() {
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.of(sampleUser));

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest));
    }

    @Test
    void register_ThrowsException_WhenUserSuspended() {
        sampleUser.setStatus(User.UserStatus.SUSPENDED);
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.of(sampleUser));

        assertThrows(AuthException.class, () -> authService.register(registerRequest));
    }

    @Test
    void register_ThrowsException_WhenAdminRoleRequested() {
        registerRequest.setRole(User.Role.ADMIN);
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.empty());

        assertThrows(AuthException.class, () -> authService.register(registerRequest));
    }

    @Test
    void register_ThrowsException_WhenDepartmentMissingForFaculty() {
        registerRequest.setRole(User.Role.FACULTY);
        registerRequest.setDepartmentId(null);

        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.empty());

        assertThrows(AuthException.class, () -> authService.register(registerRequest));
    }

    // ─────────────────────────────────────────────────────────
    // 2. LOGIN TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void login_Success() {
        when(userRepository.findByEmail(authRequest.getEmail())).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateToken(sampleUser)).thenReturn("access_token");
        when(jwtService.generateRefreshToken(sampleUser)).thenReturn("refresh_token");

        AuthResponse response = authService.login(authRequest);

        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());

        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationDto.Event.class));
    }

    @Test
    void login_ThrowsException_WhenUserSuspended() {
        sampleUser.setStatus(User.UserStatus.SUSPENDED);
        when(userRepository.findByEmail(authRequest.getEmail())).thenReturn(Optional.of(sampleUser));

        assertThrows(AuthException.class, () -> authService.login(authRequest));
    }

    @Test
    void login_ThrowsException_WhenUserInactive() {
        sampleUser.setStatus(User.UserStatus.INACTIVE);
        when(userRepository.findByEmail(authRequest.getEmail())).thenReturn(Optional.of(sampleUser));

        assertThrows(AuthException.class, () -> authService.login(authRequest));
    }

    // ─────────────────────────────────────────────────────────
    // 3. REFRESH TOKEN TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void refreshToken_Success() {
        String token = "Bearer valid_refresh_token";
        when(jwtService.extractUsername("valid_refresh_token")).thenReturn("john.doe@example.com");
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(sampleUser));
        when(jwtService.isTokenValid("valid_refresh_token", sampleUser)).thenReturn(true);
        when(jwtService.generateToken(sampleUser)).thenReturn("new_access_token");
        when(jwtService.generateRefreshToken(sampleUser)).thenReturn("new_refresh_token");

        AuthResponse response = authService.refreshToken(token);

        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertEquals("new_refresh_token", response.getRefreshToken());
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void refreshToken_ThrowsException_WhenTokenInvalid() {
        String token = "invalid_token";
        when(jwtService.extractUsername("invalid_token")).thenReturn("john.doe@example.com");
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(sampleUser));
        when(jwtService.isTokenValid("invalid_token", sampleUser)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.refreshToken(token));
    }
}