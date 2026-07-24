package com.campuscore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public class GradeDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EnterGradeRequest {
        private Long studentId;
        private BigDecimal marksObtained;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SubmitGradesRequest {
        private Long examId;
        private java.util.List<EnterGradeRequest> grades;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private Long gradeId;
        private Long examId;
        private String courseCode;
        private String courseName;
        private Long studentId;
        private String studentName;
        private BigDecimal marksObtained;
        private BigDecimal maxMarks;
        private String grade; // e.g. A, B, C, F
        private String status; // DRAFT, SUBMITTED, PUBLISHED
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ResultResponse {
        private Long resultId;
        private Long studentId;
        private String studentName;
        private String academicYear;
        private Integer semester;
        private BigDecimal sgpa;
        private BigDecimal cgpa;
        private Integer backlogs;
        private String status;
    }
}
