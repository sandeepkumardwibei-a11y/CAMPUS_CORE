package com.campuscore.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class DepartmentDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotBlank(message = "Department name is required")
        private String departmentName;
        // programId removed — a department is created standalone by name only.
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long departmentId; // Automatically generated ID returned to Admin
        private String departmentName;
        private String status;
    }
}
