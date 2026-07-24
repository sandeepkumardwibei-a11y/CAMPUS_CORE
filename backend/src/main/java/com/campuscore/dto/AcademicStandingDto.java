package com.campuscore.dto;

import lombok.*;

import java.math.BigDecimal;

public class AcademicStandingDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long studentId;
        private String studentName;
        private String academicYear;
        private Integer semester;
        private BigDecimal cgpa;
        private BigDecimal sgpa;
        // Automatic ranking based on CGPA
        private String ranking;   // EXCELLENT / GOOD / AVERAGE / POOR / NOT_AVAILABLE
        private String remark;    // Human-friendly remark
    }
}
