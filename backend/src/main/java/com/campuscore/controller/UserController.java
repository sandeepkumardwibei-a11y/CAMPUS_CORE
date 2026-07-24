package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.entity.User;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        // Log trace at request entry point using safe userDetails username
        log.info("Processing getCurrentUser endpoint request for user: {}", userDetails.getUsername());
        
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Log trace at successful response point
        log.info("Successfully processed getCurrentUser endpoint request for user: {}", userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(user, "Fetched current user profile"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY','EXAM_CONTROLLER','ACCOUNTS','STUDENT')")
    public ResponseEntity<ApiResponse<List<User>>> getUsers(@RequestParam(required = false) String role) {
        // Log trace at request entry point using safe optional filter parameter
        log.info("Processing getUsers endpoint request filtered by role: {}", role);

        // A STUDENT may only look up the FACULTY list (e.g. to browse courses by faculty).
        // They cannot enumerate all users or other roles.
        boolean isStudent = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
        if (isStudent && (role == null || !role.equalsIgnoreCase("FACULTY"))) {
            throw new com.campuscore.exception.CourseException("Access Denied: students may only view the faculty list.");
        }

        List<User> users;
        if (role != null) {
            users = userRepository.findAll().stream()
                    .filter(u -> u.getRole().name().equalsIgnoreCase(role))
                    .toList();
        } else {
            users = userRepository.findAll();
        }
        
        // Log trace at successful response point
        log.info("Successfully processed getUsers endpoint request");
        return ResponseEntity.ok(ApiResponse.success(users, "Fetched users successfully"));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<User>> updateStatus(@PathVariable Long id, @RequestParam String status) {
        // Log trace at request entry point using safe parameter variables
        log.info("Processing updateStatus endpoint request for userId: {} with target status: {}", id, status);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setStatus(User.UserStatus.valueOf(status.toUpperCase()));
        userRepository.save(user);
        
        // Log trace at successful response point
        log.info("Successfully processed updateStatus endpoint request for userId: {}", id);
        return ResponseEntity.ok(ApiResponse.success(user, "User status updated successfully"));
    }
}