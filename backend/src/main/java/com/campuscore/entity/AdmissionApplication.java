package com.campuscore.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "admission_application")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AdmissionApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "applicant_name", nullable = false)
    private String applicantName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "program_id")
    private Long programId;

    @Column(name = "program_name")
    private String programName;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "academic_year")
    private String academicYear;

    @Column(name = "qualifying_score")
    private Double qualifyingScore;

    @Column(name = "father_name")
    private String fatherName;

    @Column(name = "mother_name")
    private String motherName;

    @Column(name = "identification_number")
    private String identificationNumber;

    // 🎯 ADDED: Persistent mapping for the candidate's permanent address
    @Column(name = "permanent_address", length = 1000)
    private String permanentAddress;

    @Column(name = "admission_letter_content", length = 2000)
    private String admissionLetterContent;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // 🎯 Document uploads (relative paths on disk, resolved via FileStorageService)
    @Column(name = "tenth_marksheet_path")
    private String tenthMarksheetPath;

    @Column(name = "twelfth_marksheet_path")
    private String twelfthMarksheetPath;

    @Column(name = "aadhar_card_path")
    private String aadharCardPath;

    @Column(name = "documents_uploaded_at")
    private LocalDateTime documentsUploadedAt;

    @Builder.Default
    @Column(name = "documents_verified", nullable = false)
    private boolean documentsVerified = false;

    @CreationTimestamp
    @Column(name = "application_date", nullable = false, updatable = false)
    private LocalDateTime applicationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApplicationStatus status;

    public enum ApplicationStatus {
        SUBMITTED,
        SHORTLISTED,
        NOT_SHORTLISTED,
        OFFER_ISSUED,
        OFFER_ACCEPTED,
        DOCUMENTS_VERIFIED,
        ADMISSION_LETTER_ISSUED,
        ENROLLED,
        REJECTED,
        WITHDRAWN,
        REVOKED
    }
}