package com.campuscore.dto;

import com.campuscore.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 20, message = "Name must be between 2 and 100 characters")
    @Pattern(
        regexp = "^[A-Za-z][A-Za-z .'\\-]{1,99}$",
        message = "Name must contain only letters, spaces, hyphens, apostrophes, and periods"
    )
    private String name;

    @NotBlank(message = "Email is required")
      @Size(min = 2, max = 20, message = "Name must be between 2 and 100 characters")
    @Pattern(
        regexp = "^[A-Za-z][A-Za-z .'\\-]{1,99}$",
        message = "Name must contain only letters, spaces, hyphens, apostrophes, and periods"
    )
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!.,_\\-]).+$",
        message = "Password must include at least one uppercase letter, one lowercase letter, one number, and one special character"
    )
    private String password;

    @Pattern(regexp = "^$|^\\d{10}$", message = "Phone number must contain exactly 10 digits")
    private String phone;

    @NotNull(message = "Role is required")
    private User.Role role;

    private Long departmentId;
}
