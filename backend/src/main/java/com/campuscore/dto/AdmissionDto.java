package com.campuscore.dto;
 
import com.campuscore.entity.AdmissionApplication.ApplicationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
import java.math.BigDecimal;
import java.time.LocalDate;
 
public class AdmissionDto {
 
    // 🎯 NEW: Dedicated, clean request structure for application entry
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApplicationRequest {
        @NotBlank private String applicantName;
        @NotBlank @Email private String email;
        private String phone;
        @NotBlank private String programName;      // 🎯 Student inputs text string name
        @NotBlank private String departmentName;   // 🎯 Student inputs text string name
        @NotBlank private String academicYear;
        @NotNull private Double percentageSecured; // 🎯 Requested field name
    }
 
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank private String applicantName;
        @NotBlank @Email private String email;
        private String phone;
        @NotNull private Long programId;
        @NotBlank private String academicYear;
        private BigDecimal qualifyingScore;
    }
 
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private Long applicationId;
        private String applicantName;
        private String email;
        private String phone;
        private Long programId;
        private String programName;
        private String academicYear;
        private BigDecimal qualifyingScore;
        private LocalDate applicationDate;
        private ApplicationStatus status;
    }
 
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusUpdateRequest {
        @NotNull private ApplicationStatus status;
    }
}
 