package com.campuscore.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.time.LocalTime;

public class TimetableDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        private Long courseId;
        private String dayOfWeek;

        // 🎯 FIX: Tells Jackson to expect a plain string format instead of an object
        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime startTime;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime endTime;

        private String venue;
        private String academicYear;
        private Integer semester;
    }

    // Keep your Response class exactly the same...
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long timetableId;
        private Long courseId;
        private String courseCode;
        private String courseName;
        private String dayOfWeek;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime startTime;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime endTime;

        private String venue;
        private String academicYear;
        private Integer semester;
    }
}