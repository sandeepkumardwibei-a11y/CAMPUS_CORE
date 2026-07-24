package com.campuscore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class SemesterRegistrationDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        private Long studentId;
        private Long programId;
        private String academicYear;
        private Integer semester;
        private List<Long> courseIds;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private Long registrationId;
        private Long studentId;
        private String studentName;
        private Long programId;
        private String programName;
        private String academicYear;
        private Integer semester;
        private Integer totalCredits;
        private String status;
        private List<CourseDto.Response> courses;
    }
}
