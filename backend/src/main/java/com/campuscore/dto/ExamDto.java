package com.campuscore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

public class ExamDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotNull private Long courseId;
        @NotNull @Min(value = 1, message = "Semester must be between 1 and 8") @Max(value = 8, message = "Semester must be between 1 and 8")
        private Integer semester;
        @NotBlank private String academicYear;
        @NotBlank private String examType;
        @NotNull private LocalDate examDate;
        @NotNull private LocalTime startTime;
        @Positive private Integer durationMins;
        private String venue;
        private java.math.BigDecimal maxMarks;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private Long examId;
        private Long courseId;
        private String courseName;
        private String courseCode;
        private Integer semester;
        private String academicYear;
        private String examType;
        private LocalDate examDate;
        private LocalTime startTime;
        private Integer durationMins;
        private String venue;
        private java.math.BigDecimal maxMarks;
        private String status;
    }
}
