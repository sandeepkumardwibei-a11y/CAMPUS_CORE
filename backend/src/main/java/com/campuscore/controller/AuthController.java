package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.dto.AuthRequest;
import com.campuscore.dto.AuthResponse;
import com.campuscore.dto.RegisterRequest;
import com.campuscore.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor 
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        // Log trace at request entry point
        log.info("Processing register endpoint request");
        
        AuthResponse response = authService.register(request);
        
        // Log trace at successful response point
        log.info("Successfully processed register endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        // Log trace at request entry point
        log.info("Processing login endpoint request");
        
        AuthResponse response = authService.login(request);
        
        // Log trace at successful response point
        log.info("Successfully processed login endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@RequestBody Map<String, String> body) {
        // Log trace at request entry point
        log.info("Processing refreshToken endpoint request");
        
        String refreshToken = body.get("refreshToken");
        AuthResponse response = authService.refreshToken(refreshToken);
        
        // Log trace at successful response point
        log.info("Successfully processed refreshToken endpoint request");
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }
}