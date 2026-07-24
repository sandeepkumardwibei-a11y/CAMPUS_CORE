package com.campuscore.service;

import com.campuscore.dto.AuthRequest;
import com.campuscore.dto.AuthResponse;
import com.campuscore.dto.NotificationDto;
import com.campuscore.dto.RegisterRequest;
import com.campuscore.entity.User;
import com.campuscore.entity.AuditLog;
import com.campuscore.entity.Notification.NotificationCategory;
import com.campuscore.exception.AuthException;
import com.campuscore.exception.DuplicateResourceException;
import com.campuscore.repository.UserRepository;
import com.campuscore.repository.AuditLogRepository;
import com.campuscore.repository.DepartmentRepository;
import com.campuscore.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Plugs in the SLF4J framework mapping
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher; // 🔔 Injected event publisher for automatic alerts

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Safe entry point logging using primitive payload details
        log.info("Entering register flow processing for account email: {} with target role: {}", 
                request.getEmail(), request.getRole());

        // Check account status on existing profiles before processing registration
        Optional<User> existingUserOpt = userRepository.findByEmail(request.getEmail());
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if (existingUser.getStatus() == User.UserStatus.SUSPENDED) {
                throw new AuthException("You had been removed by the admin of the college for your actions kindly contact to admin +91144889900 .");
            }
            if (existingUser.getStatus() == User.UserStatus.INACTIVE) {
                throw new AuthException("You had withdrawn the application, contact to the admin in person.");
            }
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        if (request.getRole() == User.Role.ADMIN) {
            throw new AuthException("Registration as ADMIN is not allowed via this channel.");
        }

        Long assignedDepartmentId = null;
        User.UserStatus assignedStatus = User.UserStatus.ACTIVE;

        if (request.getRole() == User.Role.APPLICANT) {
            assignedDepartmentId = null;
            assignedStatus = User.UserStatus.PENDING;
        } else if(request.getRole() == User.Role.HOSTEL_ADMIN){
            assignedDepartmentId = null; 
            assignedStatus = User.UserStatus.PENDING; 
        } else {
            if (request.getDepartmentId() == null) {
                throw new AuthException("Department ID is strictly required for the role: " + request.getRole());
            }
            if (!departmentRepository.existsById(request.getDepartmentId())) {
                throw new AuthException("The department ID " + request.getDepartmentId() + " does not exist.");
            }
            assignedDepartmentId = request.getDepartmentId();
            assignedStatus = User.UserStatus.ACTIVE;
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(request.getRole())
                .departmentId(assignedDepartmentId)
                .status(assignedStatus)
                .build();

        userRepository.save(user);

        AuditLog regLog = AuditLog.builder()
                .user(user)
                .action("USER_REGISTRATION_" + user.getRole().name())
                .module("AUTH")
                .build();
        auditLogRepository.save(regLog);

        // 🔔 AUTOMATIC NOTIFICATION: Alert new user of successful account registration
        eventPublisher.publishEvent(new NotificationDto.Event(
            user,
            String.format("Welcome to CampusCore, %s! Your account profile has been successfully generated with the role of %s.", 
                user.getName(), user.getRole().name()),
            NotificationCategory.AUTH
        ));

        // Safe completion logging using finalized database entity fields
        log.info("Successfully registered new user profile with ID: {} and email: {}", user.getUserId(), user.getEmail());

        return AuthResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    /**
     *  FIX: Intercepted and verified status before authenticationManager checks credentials
     */
    @Transactional
    public AuthResponse login(AuthRequest request) {
        // Safe entry point logging using primitive string inputs
        log.info("Entering login credential authorization validation loop for email: {}", request.getEmail());

        // 1. Pre-fetch user profile to explicitly assert baseline system status restrictions
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthException("Invalid email credentials or account does not exist."));

        if (user.getStatus() == User.UserStatus.SUSPENDED) {
            throw new AuthException("You had been removed by the admin of the college for your actions kindly contact to admin +91144889900 .");
        }
        
        if (user.getStatus() == User.UserStatus.INACTIVE) {
            throw new AuthException("You had withdrawn the application, contact to the admin in person.");
        }

        // 2. Fall back to operational authentication processing once status safety checks clear
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        AuditLog loginLog = AuditLog.builder()
                .user(user)
                .action("USER_LOGIN_SUCCESS")
                .module("AUTH")
                .build();
        auditLogRepository.save(loginLog);

        String accessToken  = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // 🔔 AUTOMATIC NOTIFICATION: Inform user of a successful login event session check
        eventPublisher.publishEvent(new NotificationDto.Event(
            user,
            "Security Alert: A new login session was authorized successfully for your CampusCore profile account.",
            NotificationCategory.AUTH
        ));

        // Safe completion logging using numeric IDs
        log.info("Successfully authenticated login credentials sequence for userId: {}", user.getUserId());

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        log.info("Entering refreshToken processing validation index framework");

        if (refreshToken == null) throw new RuntimeException("Refresh token is required");
        if (refreshToken.startsWith("Bearer ")) refreshToken = refreshToken.substring(7);

        String username = jwtService.extractUsername(refreshToken);
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        AuditLog tokenLog = AuditLog.builder()
                .user(user)
                .action("TOKEN_REFRESH_SUCCESS")
                .module("AUTH")
                .build();
        auditLogRepository.save(tokenLog);

        String newAccess = jwtService.generateToken(user);
        String newRefresh = jwtService.generateRefreshToken(user);

        // Safe completion logging using unique user id markers
        log.info("Successfully updated token sets rotation index for userId: {}", user.getUserId());

        return buildAuthResponse(user, newAccess, newRefresh);
    }

    private AuthResponse buildAuthResponse(User user, String access, String refresh) {
        return AuthResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}