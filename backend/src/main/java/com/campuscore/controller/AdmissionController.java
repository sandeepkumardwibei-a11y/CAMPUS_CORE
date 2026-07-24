package com.campuscore.controller;

import com.campuscore.dto.AdmissionDto;
import com.campuscore.entity.AdmissionApplication;
import com.campuscore.entity.OfferLetter;
import com.campuscore.service.AdmissionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added Lombok Slf4j annotation import

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j // Plugs the SLF4J log engine instance into this class automatically via Lombok
@RestController
@RequestMapping("/admissions")
@RequiredArgsConstructor
public class AdmissionController {

    private final AdmissionService admissionService;

    // STAGE 1: Candidate Entry Application Post Endpoint
    @PostMapping("/apply")
    @PreAuthorize("hasAnyAuthority('APPLICANT', 'ROLE_APPLICANT')")
    public ResponseEntity<?> apply(@RequestBody AdmissionDto.ApplicationRequest request) {
        // Log trace at request entry point
        log.info("Processing apply endpoint request");
        try {
            AdmissionApplication savedApplication = admissionService.submitApplication(request);
            // Log trace at successful response point
            log.info("Successfully processed apply endpoint request");
            return ResponseEntity.ok(savedApplication);
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing apply endpoint request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 2: Admin Evaluation Check Point
    @PostMapping("/{applicationId}/evaluate")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<?> evaluateApplication(
            @PathVariable Long applicationId,
            @RequestParam boolean approved) {
        // Log trace at request entry point
        log.info("Processing evaluateApplication endpoint request for applicationId: {}", applicationId);
        try {
            AdmissionApplication processed = admissionService.evaluateApplication(applicationId, approved);
            // Log trace at successful response point
            log.info("Successfully processed evaluateApplication endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(processed);
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing evaluateApplication endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 3: Admin Formal Offer Dispatch Generation Endpoint
    @PostMapping("/{applicationId}/issue-offer")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<?> issueOffer(
            @PathVariable Long applicationId,
            @RequestParam String feeDetailsRef) {
        // Log trace at request entry point
        log.info("Processing issueOffer endpoint request for applicationId: {}", applicationId);
        try {
            OfferLetter offer = admissionService.issueOfferLetter(applicationId, feeDetailsRef);
            // Log trace at successful response point
            log.info("Successfully processed issueOffer endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Offer letter issued successfully.",
                    "offerId", offer.getOfferId(),
                    "status", offer.getStatus(),
                    "joiningDeadline", offer.getJoiningDeadline(),
                    "content", offer.getFormalLetterContent()
            ));
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing issueOffer endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 4A: Candidate Secure Data Confirmation Step
    //  UPDATED: Added request parameter mapping path for permanentAddress string
    @PostMapping("/{applicationId}/accept-offer")
    @PreAuthorize("hasAnyAuthority('APPLICANT', 'ROLE_APPLICANT')")
    public ResponseEntity<?> acceptOffer(
            @PathVariable Long applicationId,
            @RequestParam String fatherName,
            @RequestParam String motherName,
            @RequestParam String identificationNumber,
            @RequestParam String permanentAddress) {
        // Log trace at request entry point
        log.info("Processing acceptOffer endpoint request for applicationId: {}", applicationId);
        try {
            AdmissionApplication acceptedApp = admissionService.acceptOffer(applicationId, fatherName, motherName, identificationNumber, permanentAddress);
            // Log trace at successful response point
            log.info("Successfully processed acceptOffer endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Offer accepted successfully.",
                    "applicationId", acceptedApp.getApplicationId(),
                    "status", acceptedApp.getStatus(),
                    "applicantName", acceptedApp.getApplicantName()
            ));
        } catch (Exception e) {
            // Log trace at error response point
            log.error("Severe error processing acceptOffer endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Root Cause: " + e.getMessage()
            ));
        }
    }

    // STAGE 4B: Candidate Regret Processing Route
    @PostMapping("/{applicationId}/reject-offer")
    @PreAuthorize("hasAnyAuthority('APPLICANT', 'ROLE_APPLICANT')")
    public ResponseEntity<?> rejectOffer(
            @PathVariable Long applicationId,
            @RequestParam(required = false) String reasonMessage) {
        // Log trace at request entry point
        log.info("Processing rejectOffer endpoint request for applicationId: {}", applicationId);
        try {
            AdmissionApplication rejectedApplication = admissionService.rejectOffer(applicationId, reasonMessage);
            // Log trace at successful response point
            log.info("Successfully processed rejectOffer endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(rejectedApplication);
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing rejectOffer endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 4C: Candidate Voluntary Application Withdrawal
    //  NEW FEATURE: Student can withdraw their own application, erasing profile fields
    @PostMapping("/{applicationId}/withdraw")
    @PreAuthorize("hasAnyAuthority('APPLICANT', 'ROLE_APPLICANT')")
    public ResponseEntity<?> withdrawApplication(@PathVariable Long applicationId) {
        // Log trace at request entry point
        log.info("Processing withdrawApplication endpoint request for applicationId: {}", applicationId);
        try {
            AdmissionApplication withdrawnApp = admissionService.withdrawApplication(applicationId);
            // Log trace at successful response point
            log.info("Successfully processed withdrawApplication endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Application has been successfully withdrawn and identifying details cleared.",
                    "applicationId", withdrawnApp.getApplicationId(),
                    "status", withdrawnApp.getStatus()
            ));
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing withdrawApplication endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 4D: Administrative Forceful Offer Revocation Check Point
    //  NEW FEATURE: Admin can revoke an application by providing a mandatory reason message
    @PostMapping("/{applicationId}/revoke-offer")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<?> revokeOffer(
            @PathVariable Long applicationId,
            @RequestParam String reasonMessage) {
        // Log trace at request entry point
        log.info("Processing revokeOffer endpoint request for applicationId: {}", applicationId);
        try {
            AdmissionApplication revokedApp = admissionService.revokeOfferByAdmin(applicationId, reasonMessage);
            // Log trace at successful response point
            log.info("Successfully processed revokeOffer endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "The active offer has been forcefully revoked and data fields anonymized.",
                    "applicationId", revokedApp.getApplicationId(),
                    "status", revokedApp.getStatus(),
                    "reason", revokedApp.getRejectionReason()
            ));
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing revokeOffer endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 5: Admin Query Interface Profile Audit Endpoint
    //  UPDATED: Allows admin, or strictly checks that the applicant matches the logged-in user context identity
    @GetMapping("/{applicationId}/verification-details")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getVerificationDetails(@PathVariable Long applicationId) {
        // Log trace at request entry point
        log.info("Processing getVerificationDetails endpoint request for applicationId: {}", applicationId);
        try {
            AdmissionApplication application = admissionService.getDetailsForVerification(applicationId);
            
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("ROLE_ADMIN"));
            
            if (!isAdmin && !application.getEmail().equalsIgnoreCase(username)) {
                // Log trace at authorization denial point
                log.warn("Access Denied tracking getVerificationDetails endpoint request for applicationId: {}", applicationId);
                return ResponseEntity.status(430).body(Map.of("success", false, "message", "Access Denied: You do not have permission to view another student's application metadata profile."));
            }

            // Log trace at successful response point
            log.info("Successfully processed getVerificationDetails endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(application);
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing getVerificationDetails endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 6: Admin Quality Scan Action Endpoint
    @PostMapping("/{applicationId}/verify-documents")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<?> verifyDocuments(
            @PathVariable Long applicationId,
            @RequestParam boolean isVerified) {
        // Log trace at request entry point
        log.info("Processing verifyDocuments endpoint request for applicationId: {}", applicationId);
        try {
            AdmissionApplication verifiedApp = admissionService.processDocumentVerification(applicationId, isVerified);
            // Log trace at successful response point
            log.info("Successfully processed verifyDocuments endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(verifiedApp);
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing verifyDocuments endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 7: Admin Final Letter Issue Generation Point
    @PostMapping("/{applicationId}/issue-admission-letter")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<?> issueAdmissionLetter(@PathVariable Long applicationId) {
        // Log trace at request entry point
        log.info("Processing issueAdmissionLetter endpoint request for applicationId: {}", applicationId);
        try {
            AdmissionApplication letterIssuedApp = admissionService.issueAdmissionLetter(applicationId);
            // Log trace at successful response point
            log.info("Successfully processed issueAdmissionLetter endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(letterIssuedApp);
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing issueAdmissionLetter endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 8: Final Student Signoff and Conversion Execution Point
    @PostMapping("/{applicationId}/finalize-enrollment")
    @PreAuthorize("hasAnyAuthority('APPLICANT', 'ROLE_APPLICANT', 'STUDENT', 'ROLE_STUDENT')")
    public ResponseEntity<?> finalizeEnrollment(@PathVariable Long applicationId) {
        // Log trace at request entry point
        log.info("Processing finalizeEnrollment endpoint request for applicationId: {}", applicationId);
        try {
            AdmissionApplication finalEnrolled = admissionService.finalizeEnrollment(applicationId);
            // Log trace at successful response point
            log.info("Successfully processed finalizeEnrollment endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(finalEnrolled);
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing finalizeEnrollment endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 5B: Candidate document upload (10th / 12th marksheet, Aadhar card)
    @PostMapping("/{applicationId}/documents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadDocument(
            @PathVariable Long applicationId,
            @RequestParam String docType,
            @RequestParam("file") MultipartFile file) {
        log.info("Processing uploadDocument endpoint request for applicationId: {}, docType: {}", applicationId, docType);
        try {
            AdmissionApplication updated = admissionService.uploadDocument(applicationId, docType, file);
            log.info("Successfully processed uploadDocument endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Document uploaded successfully.",
                    "applicationId", updated.getApplicationId()
            ));
        } catch (Exception e) {
            log.warn("Error processing uploadDocument endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 5C: Document summary (which documents have been uploaded / verified)
    @GetMapping("/{applicationId}/documents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDocumentSummary(@PathVariable Long applicationId) {
        try {
            return ResponseEntity.ok(admissionService.getDocumentSummary(applicationId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // STAGE 5D: View / download a specific uploaded document (owner or admin)
    @GetMapping("/{applicationId}/documents/{docType}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> viewDocument(@PathVariable Long applicationId, @PathVariable String docType) {
        try {
            byte[] bytes = admissionService.downloadDocument(applicationId, docType);
            String contentType = admissionService.resolveDocumentContentType(applicationId, docType);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + docType.toLowerCase() + "\"")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Lightweight applicant meta (works at any stage; admin or owner)
    @GetMapping("/{applicationId}/basic-info")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getBasicInfo(@PathVariable Long applicationId) {
        log.info("Processing getBasicInfo endpoint request for applicationId: {}", applicationId);
        try {
            return ResponseEntity.ok(admissionService.getBasicInfo(applicationId));
        } catch (Exception e) {
            log.warn("Error processing getBasicInfo endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Legacy Support Core Query Endpoints
    @GetMapping("/{applicationId}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getApplicationStatus(@PathVariable Long applicationId) {
        // Log trace at request entry point
        log.info("Processing getApplicationStatus endpoint request for applicationId: {}", applicationId);
        try {
            AdmissionApplication.ApplicationStatus status = admissionService.getApplicationStatus(applicationId);
            // Log trace at successful response point
            log.info("Successfully processed getApplicationStatus endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(Map.of("applicationId", applicationId, "status", status));
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing getApplicationStatus endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Core Offer Extraction Endpoint
    //  UPDATED: Restricted so only admins or the explicit owner student identity can view this flat map output.
    @GetMapping("/{applicationId}/offer-details")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getOfferLetterDetails(@PathVariable Long applicationId) {
        // Log trace at request entry point
        log.info("Processing getOfferLetterDetails endpoint request for applicationId: {}", applicationId);
        try {
            OfferLetter offer = admissionService.getOfferLetterByApplicationId(applicationId);
            
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("ROLE_ADMIN"));
            
            if (!isAdmin && !offer.getApplication().getEmail().equalsIgnoreCase(username)) {
                // Log trace at authorization denial point
                log.warn("Access Denied tracking getOfferLetterDetails endpoint request for applicationId: {}", applicationId);
                return ResponseEntity.status(430).body(Map.of("success", false, "message", "Access Denied: You do not have permission to view another student's offer details."));
            }

            // Log trace at successful response point
            log.info("Successfully processed getOfferLetterDetails endpoint request for applicationId: {}", applicationId);
            return ResponseEntity.ok(Map.of(
                    "offerId", offer.getOfferId(),
                    "academicYear", offer.getAcademicYear() != null ? offer.getAcademicYear() : "",
                    "issueDate", offer.getIssueDate() != null ? offer.getIssueDate().toString() : "",
                    "joiningDeadline", offer.getJoiningDeadline() != null ? offer.getJoiningDeadline().toString() : "",
                    "feeDetailsRef", offer.getFeeDetailsRef() != null ? offer.getFeeDetailsRef() : "",
                    "status", offer.getStatus() != null ? offer.getStatus().toString() : "",
                    "formalLetterContent", offer.getFormalLetterContent() != null ? offer.getFormalLetterContent() : ""
            ));
        } catch (Exception e) {
            // Log trace at error response point
            log.warn("Error processing getOfferLetterDetails endpoint request for applicationId {}: {}", applicationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}