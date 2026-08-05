package com.campuscore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class CourseDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Course name is required")
        private String courseName;

        @NotBlank(message = "Course code is required")
        private String courseCode;

        @NotNull(message = "Credits are required")
        @Min(value = 1, message = "Credits must be between 1 and 8")
        @Max(value = 8, message = "Credits must be between 1 and 8")
        private Integer credits;

        // MANY-TO-MANY: one or more programs this course belongs to (selected as
        // a dropdown of registered program names on the frontend).
        private List<Long> programIds;

        @Min(value = 1, message = "Semester must be between 1 and 8")
        @Max(value = 8, message = "Semester must be between 1 and 8")
        private Integer semester;
        private Long facultyId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private Long courseId;
        private String courseName;
        private String courseCode;
        private List<Long> programIds;
        private List<String> programNames;
        private Integer semester;
        private Integer credits;
        private Long facultyId;
        private String facultyName;
        private String status;
    }
}
