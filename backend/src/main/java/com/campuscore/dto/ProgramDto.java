package com.campuscore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

public class ProgramDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank(message = "Program name is required")
        private String programName;

        @NotBlank(message = "Level is required (UG, PG, PHD, DIPLOMA)")
        private String level;

        @NotNull(message = "Duration in years is required")
        private Integer durationYears;

        @NotNull(message = "Total seats are required")
        @Min(value = 100, message = "Total seats must be between 100 and 1000")
        @Max(value = 1000, message = "Total seats must be between 100 and 1000")
        private Integer totalSeats;

        @NotNull(message = "Minimum aggregate percentage is required")
        @Min(value = 0, message = "Minimum percentage must be between 0 and 100")
        @Max(value = 100, message = "Minimum percentage must be between 0 and 100")
        private Double minimumPercentage;

        // A program must belong to exactly one department (one dept -> many programs).
        @NotNull(message = "A department must be selected for this program")
        private Long departmentId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long programId;
        private String programName;
        private Long departmentId;
        private String departmentName;
        private String level;
        private Integer durationYears;
        private Integer totalSeats;
        private Double minimumPercentage;
        private String status;
    }
}
