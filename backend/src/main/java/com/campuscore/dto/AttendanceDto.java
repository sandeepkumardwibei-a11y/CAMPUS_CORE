package com.campuscore.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class AttendanceDto {


    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MarkRequest {
        @NotNull private Long courseId;

        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate lectureDate;

        @NotNull private List<StudentAttendance> records;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StudentAttendance {
        @NotNull 
        @Size(min=1, max=1)
        private Long studentId;
        @NotBlank private String status; // PRESENT / ABSENT / LATE / OFFICIAL_DUTY
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SummaryResponse {
        private Long summaryId;
        private Long studentId;
        private String studentName;
        private Long courseId;
        private String courseName;
        private Integer semester;
        private String academicYear;
        private Integer totalLectures;
        private Integer attendedLectures;
        private java.math.BigDecimal attendancePercent;
        private Boolean shortageFlag;
        // Per-status breakdown (used by the pie chart on the frontend).
        private Integer presentCount;
        private Integer lateCount;
        private Integer absentCount;
        private Integer officialDutyCount;
        // True when OFFICIAL_DUTY was added to lift the student out of a shortage.
        private Boolean officialDutyApplied;
    }



    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FacultyMarkRequest {
        @NotBlank private String facultyName;

        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate date;

        @NotBlank private String status; // PRESENT / ABSENT
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FacultyResponse {
        private Long id;
        private Long facultyId;
        private String facultyName;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate date;

        private String status;
    }
}