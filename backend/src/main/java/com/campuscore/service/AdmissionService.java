package com.campuscore.service;

import com.campuscore.dto.AdmissionDto;
import com.campuscore.dto.NotificationDto;
import com.campuscore.entity.AdmissionApplication;
import com.campuscore.entity.OfferLetter;
import com.campuscore.entity.Program;
import com.campuscore.entity.Department;
import com.campuscore.entity.User;
import com.campuscore.entity.Notification.NotificationCategory;
import com.campuscore.exception.AdmissionException;
import com.campuscore.exception.ResourceNotFoundException;
import com.campuscore.repository.AdmissionApplicationRepository;
import com.campuscore.repository.OfferLetterRepository;
import com.campuscore.repository.ProgramRepository;
import com.campuscore.repository.DepartmentRepository;
import com.campuscore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final AdmissionApplicationRepository admissionRepository;
    private final UserRepository userRepository;
    private final ProgramRepository programRepository;
    private final OfferLetterRepository offerLetterRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FileStorageService fileStorageService;

    private static final List<String> ALLOWED_DOC_TYPES = List.of("TENTH", "TWELFTH", "AADHAR");

    // ADMIN LIST VIEW: all applications currently in the pipeline. Applications that
    // have reached a terminal, no-longer-actionable state — enrolled, withdrawn,
    // rejected, or revoked — are excluded from this view.
    public List<AdmissionDto.ListItem> getAllApplications() {
        log.info("Entering getAllApplications execution flow for admin application list view");
        List<AdmissionApplication.ApplicationStatus> excluded = List.of(
                AdmissionApplication.ApplicationStatus.ENROLLED,
                AdmissionApplication.ApplicationStatus.WITHDRAWN,
                AdmissionApplication.ApplicationStatus.REJECTED,
                AdmissionApplication.ApplicationStatus.REVOKED
        );
        return admissionRepository.findByStatusNotIn(excluded).stream()
                .map(a -> AdmissionDto.ListItem.builder()
                        .applicationId(a.getApplicationId())
                        .applicantName(a.getApplicantName())
                        .email(a.getEmail())
                        .programName(a.getProgramName())
                        .status(a.getStatus() != null ? a.getStatus().name() : null)
                        .applicationDate(a.getApplicationDate())
                        .build())
                .toList();
    }

    // STAGE 1: SUBMIT APPLICATION (STUDENT ACTIVITY)
    @Transactional
    public AdmissionApplication submitApplication(AdmissionDto.ApplicationRequest request) {
        log.info("Entering submitApplication execution flow for applicant email: {} and program: {}",
                request.getEmail(), request.getProgramName());

        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new AdmissionException("Access Denied: Logged-in user profile not found."));

        if (currentUser.getStatus() == User.UserStatus.INACTIVE) {
            throw new AdmissionException("You had withdrawn the application, contact to the admin in person.");
        }

        if (currentUser.getStatus() == User.UserStatus.SUSPENDED) {
            throw new AdmissionException("You had been removed by the admin of the college for your actions kindly contact to admin +91144889900 .");
        }

        if (!request.getEmail().equalsIgnoreCase(currentUser.getEmail())) {
            throw new AdmissionException("Access Denied: The email provided does not match your registered account email.");
        }

        if (!request.getApplicantName().equalsIgnoreCase(currentUser.getName())) {
            throw new AdmissionException("Access Denied: The applicant name does not match your official profile name.");
        }

        List<AdmissionApplication> existingApplications = admissionRepository.findAll();
        if (!existingApplications.isEmpty()) {
            for (AdmissionApplication existingApp : existingApplications) {
                String withdrawnTarget = "withdrawn_" + existingApp.getApplicationId() + "@campuscore.anonymous";
                String revokedTarget = "revoked_" + existingApp.getApplicationId() + "@campuscore.anonymous";
                boolean matchesActiveEmail = existingApp.getEmail().equalsIgnoreCase(authenticatedEmail);
                boolean matchesAnonymizedWithdrawn = existingApp.getEmail().equalsIgnoreCase(withdrawnTarget) && matchesUserContext(existingApp, currentUser);
                boolean matchesAnonymizedRevoked = existingApp.getEmail().equalsIgnoreCase(revokedTarget) && matchesUserContext(existingApp, currentUser);
                if (matchesActiveEmail || matchesAnonymizedWithdrawn || matchesAnonymizedRevoked) {
                    if (existingApp.getStatus() == AdmissionApplication.ApplicationStatus.WITHDRAWN) {
                        throw new AdmissionException("You had withdrawn the application, contact to the admin in person.");
                    }
                    if (existingApp.getStatus() == AdmissionApplication.ApplicationStatus.REVOKED) {
                        throw new AdmissionException("You had been removed by the admin of the college for your actions kindly contact to admin +91144889900 .");
                    }
                    throw new AdmissionException("Access Denied: You have an active or un-withdrawn application processing.");
                }
            }
        }

        if (request.getProgramName() == null || request.getProgramName().trim().isEmpty()) {
            throw new AdmissionException("Validation Error: Program name is required.");
        }
        Program program = programRepository.findByProgramNameIgnoreCase(request.getProgramName().trim())
                .orElseThrow(() -> new ResourceNotFoundException("The specified program does not exist."));

        if (request.getDepartmentName() == null || request.getDepartmentName().trim().isEmpty()) {
            throw new AdmissionException("Validation Error: Department name is required.");
        }
        Department department = departmentRepository.findByDepartmentNameIgnoreCase(request.getDepartmentName().trim())
                .orElseThrow(() -> new ResourceNotFoundException("The specified department does not exist."));

        // The chosen program must belong to the chosen department (one dept -> many programs).
        if (program.getDepartmentId() == null || !program.getDepartmentId().equals(department.getDepartmentId())) {
            throw new AdmissionException("The selected program is not offered under the selected department.");
        }

        AdmissionApplication application = AdmissionApplication.builder()
                .applicantName(request.getApplicantName())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone())
                .programId(program.getProgramId())
                .programName(program.getProgramName())
                .departmentId(department.getDepartmentId())
                .departmentName(department.getDepartmentName())
                .academicYear(request.getAcademicYear())
                .qualifyingScore(request.getPercentageSecured())
                .status(AdmissionApplication.ApplicationStatus.SUBMITTED)
                .build();

        AdmissionApplication savedApplication = admissionRepository.saveAndFlush(application);

        // 🔔 AUTOMATIC NOTIFICATION
        eventPublisher.publishEvent(new NotificationDto.Event(
                currentUser,
                String.format("Admission Update: Your application for %s under the department of %s has been successfully submitted.",
                        savedApplication.getProgramName(), savedApplication.getDepartmentName()),
                NotificationCategory.ADMISSIONS
        ));

        log.info("Successfully processed submitApplication. Created application database record ID: {}", savedApplication.getApplicationId());
        return savedApplication;
    }

    // STAGE 2: EVALUATE APPLICATION (ADMIN ACTIVITY)
    @Transactional
    public AdmissionApplication evaluateApplication(Long applicationId, boolean approved) {
        log.info("Entering evaluateApplication execution flow for applicationId: {} with approval decision: {}", applicationId, approved);

        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        if (application.getStatus() != AdmissionApplication.ApplicationStatus.SUBMITTED) {
            throw new AdmissionException("Validation Error: This application has already been processed out of the evaluation queue.");
        }

        Program program = programRepository.findById(application.getProgramId())
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", application.getProgramId()));

        double studentPercentage = application.getQualifyingScore();
        double programMinPercentage = program.getMinimumPercentage();

        if (approved) {
            if (studentPercentage < programMinPercentage) {
                throw new AdmissionException(String.format(
                        "Action Denied: Cannot shortlist applicant. Secured score (%.2f%%) is below the minimum required cut-off (%.2f%%) for %s.",
                        studentPercentage, programMinPercentage, program.getProgramName()
                ));
            }
            application.setStatus(AdmissionApplication.ApplicationStatus.SHORTLISTED);
            application.setRejectionReason(null);
        } else {
            application.setStatus(AdmissionApplication.ApplicationStatus.NOT_SHORTLISTED);
            application.setRejectionReason("Application turned down during evaluation screening panel reviews.");
        }

        AdmissionApplication updatedApplication = admissionRepository.saveAndFlush(application);

        // 🔔 AUTOMATIC NOTIFICATION
        User studentUser = userRepository.findByEmail(authenticatedUserEmailLookupFallback(updatedApplication.getEmail()))
                .orElse(null);
        if (studentUser != null) {
            String screeningMessage = approved
                    ? String.format("Admission Update: Congratulations! Your application for %s has been SHORTLISTED after evaluation screening.", updatedApplication.getProgramName())
                    : String.format("Admission Update: Your application for %s has been reviewed and was not shortlisted.", updatedApplication.getProgramName());

            eventPublisher.publishEvent(new NotificationDto.Event(studentUser, screeningMessage, NotificationCategory.ADMISSIONS));
        }

        log.info("Successfully completed evaluateApplication workflow for applicationId: {}", applicationId);
        return updatedApplication;
    }

    // STAGE 3: ISSUE OFFER LETTER (ADMIN ACTIVITY)
    @Transactional
    public OfferLetter issueOfferLetter(Long applicationId, String feeDetailsRef) {
        log.info("Entering issueOfferLetter execution flow for applicationId: {} with fee details reference: {}", applicationId, feeDetailsRef);

        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        if (application.getStatus() == AdmissionApplication.ApplicationStatus.OFFER_ISSUED) {
            OfferLetter existingLetter = offerLetterRepository.findByApplication(application)
                    .orElseThrow(() -> new AdmissionException("Data Inconsistency: Application status is OFFER_ISSUED but no corresponding offer letter record exists."));
            log.info("Returning pre-existing offer letter for application status checking on record ID: {}", existingLetter.getOfferId());
            return existingLetter;
        }
        if (application.getStatus() == AdmissionApplication.ApplicationStatus.SUBMITTED) {
            throw new AdmissionException("Action Denied: The application is yet to receive your manual approval. Please evaluate the application first.");
        }
        if (application.getStatus() != AdmissionApplication.ApplicationStatus.SHORTLISTED) {
            throw new AdmissionException("Action Denied: The application cannot be issued an offer letter because its current status is: " + application.getStatus() + " (Expected: SHORTLISTED)");
        }

        Program program = programRepository.findById(application.getProgramId())
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", application.getProgramId()));

        String formalLetterText = "Dear Candidate, you got an offer from our institution for the program "
                + program.getProgramName() + ". Kindly accept the offer in the next 14 days, otherwise it will get revoked automatically.";
        LocalDate issueDate = LocalDate.now();
        LocalDate deadlineDate = issueDate.plusDays(14);

        OfferLetter offerLetter = OfferLetter.builder()
                .application(application)
                .program(program)
                .academicYear(application.getAcademicYear())
                .issueDate(issueDate)
                .feeDetailsRef(feeDetailsRef)
                .joiningDeadline(deadlineDate)
                .status(OfferLetter.OfferStatus.ISSUED)
                .formalLetterContent(formalLetterText)
                .build();

        application.setStatus(AdmissionApplication.ApplicationStatus.OFFER_ISSUED);
        admissionRepository.saveAndFlush(application);
        OfferLetter savedOffer = offerLetterRepository.saveAndFlush(offerLetter);

        // 🔔 AUTOMATIC NOTIFICATION
        userRepository.findByEmail(application.getEmail()).ifPresent(student -> {
            eventPublisher.publishEvent(new NotificationDto.Event(
                    student,
                    String.format("Admission Alert: Your formal Offer Letter for %s has been issued! Please review and accept before %s.",
                            program.getProgramName(), deadlineDate.toString()),
                    NotificationCategory.ADMISSIONS
            ));
        });

        log.info("Successfully generated and processed offer letter with entity ID: {} for applicationId: {}", savedOffer.getOfferId(), applicationId);
        return savedOffer;
    }

    // STAGE 4A: ACCEPT OFFER (STUDENT ACTIVITY)
    @Transactional
    public AdmissionApplication acceptOffer(Long applicationId, String fatherName, String motherName, String identificationNumber, String permanentAddress) {
        log.info("Entering acceptOffer execution flow for applicationId: {}", applicationId);

        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        if (application.getStatus() == AdmissionApplication.ApplicationStatus.WITHDRAWN) {
            throw new AdmissionException("You had withdrawn the application, contact to the admin in person.");
        }
        if (application.getStatus() == AdmissionApplication.ApplicationStatus.REVOKED) {
            throw new AdmissionException("You had been removed by the admin of the college for your actions kindly contact to admin +91144889900 .");
        }

        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (authenticatedEmail == null || !application.getEmail().trim().equalsIgnoreCase(authenticatedEmail.trim())) {
            throw new AdmissionException("Access Denied: You cannot accept an offer issued to another applicant.");
        }
        if (application.getStatus() == AdmissionApplication.ApplicationStatus.OFFER_ACCEPTED) {
            log.info("Application with ID: {} has already accepted the generated offer. Terminating accept flow early.", applicationId);
            return application;
        }
        if (application.getStatus() != AdmissionApplication.ApplicationStatus.OFFER_ISSUED) {
            throw new AdmissionException("Action Denied: No valid offer has been officially issued for this application to accept. Current status: " + application.getStatus());
        }

        OfferLetter offer = offerLetterRepository.findByApplication(application)
                .orElseThrow(() -> new AdmissionException("No active offer letter found for this application reference ID."));

        if (offer.getJoiningDeadline() != null) {
            if (LocalDate.now().isAfter(offer.getJoiningDeadline())) {
                offer.setStatus(OfferLetter.OfferStatus.REVOKED);
                offerLetterRepository.saveAndFlush(offer);
                application.setStatus(AdmissionApplication.ApplicationStatus.REJECTED);
                application.setRejectionReason("Offer expired: Candidate failed to accept within the mandatory timeframe.");
                admissionRepository.saveAndFlush(application);
                throw new AdmissionException("Action Denied: This offer letter has expired. Your application is now rejected.");
            }
        }

        application.setFatherName(fatherName);
        application.setMotherName(motherName);
        application.setIdentificationNumber(identificationNumber);
        application.setPermanentAddress(permanentAddress);
        application.setStatus(AdmissionApplication.ApplicationStatus.OFFER_ACCEPTED);

        offer.setStatus(OfferLetter.OfferStatus.ACCEPTED);
        offerLetterRepository.saveAndFlush(offer);
        AdmissionApplication updatedApplication = admissionRepository.saveAndFlush(application);

        // 🔔 AUTOMATIC NOTIFICATION
        userRepository.findByEmail(authenticatedEmail).ifPresent(student -> {
            eventPublisher.publishEvent(new NotificationDto.Event(
                    student,
                    String.format("Admission Update: You have accepted the admission offer for %s. Next stage: Document Verification.", updatedApplication.getProgramName()),
                    NotificationCategory.ADMISSIONS
            ));
        });

        log.info("Successfully processed offer acceptance updates for applicationId: {}", applicationId);
        return updatedApplication;
    }

    // STAGE 4B: REJECT OFFER (STUDENT ACTIVITY)
    @Transactional
    public AdmissionApplication rejectOffer(Long applicationId, String reasonMessage) {
        log.info("Entering rejectOffer execution flow for applicationId: {} due to target tracking explanation: {}", applicationId, reasonMessage);

        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        if (application.getStatus() == AdmissionApplication.ApplicationStatus.WITHDRAWN) {
            throw new AdmissionException("You had withdrawn the application, contact to the admin in person.");
        }
        if (application.getStatus() == AdmissionApplication.ApplicationStatus.REVOKED) {
            throw new AdmissionException("You had been removed by the admin of the college for your actions kindly contact to admin +91144889900 .");
        }

        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!application.getEmail().trim().equalsIgnoreCase(authenticatedEmail.trim())) {
            throw new AdmissionException("Access Denied: You cannot reject an offer issued to another applicant.");
        }
        if (application.getStatus() == AdmissionApplication.ApplicationStatus.REJECTED) {
            throw new AdmissionException("Action Cancelled: This offer has already been rejected.");
        }

        OfferLetter offer = offerLetterRepository.findByApplication(application)
                .orElseThrow(() -> new AdmissionException("No active offer letter found."));

        offer.setStatus(OfferLetter.OfferStatus.REVOKED);
        offerLetterRepository.saveAndFlush(offer);

        application.setStatus(AdmissionApplication.ApplicationStatus.REJECTED);
        application.setRejectionReason(reasonMessage != null ? reasonMessage : "Voluntarily turned down by applicant.");
        AdmissionApplication rejectedApplication = admissionRepository.saveAndFlush(application);

        // 🔔 AUTOMATIC NOTIFICATION
        userRepository.findByEmail(authenticatedEmail).ifPresent(student -> {
            eventPublisher.publishEvent(new NotificationDto.Event(
                    student,
                    String.format("Admission System Notice: You have voluntarily declined the admission offer for %s.", rejectedApplication.getProgramName()),
                    NotificationCategory.ADMISSIONS
            ));
        });

        log.info("Successfully updated offer status records to REJECTED for applicationId: {}", applicationId);
        return rejectedApplication;
    }

    // STAGE 4C: WITHDRAW APPLICATION (STUDENT ACTIVITY)
    @Transactional
    public AdmissionApplication withdrawApplication(Long applicationId) {
        log.info("Entering withdrawApplication execution flow for applicationId: {}", applicationId);

        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        if (application.getStatus() == AdmissionApplication.ApplicationStatus.WITHDRAWN) {
            throw new AdmissionException("You had withdrawn the application, contact to the admin in person.");
        }
        if (application.getStatus() == AdmissionApplication.ApplicationStatus.REVOKED) {
            throw new AdmissionException("You had been removed by the admin of the college for your actions kindly contact to admin +91144889900 .");
        }

        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (authenticatedEmail == null || !application.getEmail().trim().equalsIgnoreCase(authenticatedEmail.trim())) {
            throw new AdmissionException("Access Denied: You can only withdraw your own admission application.");
        }

        String originalEmail = application.getEmail();
        User studentUser = userRepository.findByEmail(originalEmail).orElse(null);

        userRepository.findByEmail(originalEmail).ifPresent(user -> {
            user.setStatus(User.UserStatus.INACTIVE);
            userRepository.saveAndFlush(user);
            application.setFatherName(currentUserIdentificationHash(user));
        });

        String previousProgramName = application.getProgramName();
        application.setApplicantName("WITHDRAWN_CANDIDATE");
        application.setEmail("withdrawn_" + applicationId + "@campuscore.anonymous");
        application.setPhone(null);
        application.setMotherName(null);
        application.setIdentificationNumber(null);
        application.setPermanentAddress(null);
        application.setQualifyingScore(0.0);
        application.setAdmissionLetterContent(null);
        application.setStatus(AdmissionApplication.ApplicationStatus.WITHDRAWN);
        application.setRejectionReason("Application voluntarily withdrawn by the applicant.");

        offerLetterRepository.findByApplication(application).ifPresent(offer -> {
            offer.setStatus(OfferLetter.OfferStatus.REVOKED);
            offerLetterRepository.saveAndFlush(offer);
        });
        AdmissionApplication withdrawnApplication = admissionRepository.saveAndFlush(application);

        // 🔔 AUTOMATIC NOTIFICATION
        if (studentUser != null) {
            eventPublisher.publishEvent(new NotificationDto.Event(
                    studentUser,
                    String.format("Admission Notice: Your application submission for %s has been successfully withdrawn. Your registration is now INACTIVE.", previousProgramName),
                    NotificationCategory.ADMISSIONS
            ));
        }

        log.info("Successfully completed voluntary withdrawApplication flow context records for applicationId: {}", applicationId);
        return withdrawnApplication;
    }

    // STAGE 4D: REVOKE OFFER (ADMIN ACTIVITY)
    @Transactional
    public AdmissionApplication revokeOfferByAdmin(Long applicationId, String revokeReason) {
        log.info("Entering administrative revokeOfferByAdmin workflow execution context for applicationId: {}", applicationId);
        if (revokeReason == null || revokeReason.trim().isEmpty()) {
            throw new AdmissionException("Validation Error: A specific reason must be provided by administration to revoke an active offer.");
        }

        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        if (application.getStatus() == AdmissionApplication.ApplicationStatus.WITHDRAWN) {
            throw new AdmissionException("This application has already been voluntarily withdrawn by the candidate.");
        }
        if (application.getStatus() == AdmissionApplication.ApplicationStatus.REVOKED) {
            throw new AdmissionException("This application has already been officially revoked.");
        }

        String studentEmail = application.getEmail();
        User studentUser = userRepository.findByEmail(studentEmail).orElse(null);

        userRepository.findByEmail(studentEmail).ifPresent(user -> {
            user.setStatus(User.UserStatus.SUSPENDED);
            userRepository.saveAndFlush(user);
            application.setFatherName(currentUserIdentificationHash(user));
        });

        String targetingProgram = application.getProgramName();
        application.setApplicantName("REVOKED_CANDIDATE");
        application.setEmail("revoked_" + applicationId + "@campuscore.anonymous");
        application.setPhone(null);
        application.setMotherName(null);
        application.setIdentificationNumber(null);
        application.setPermanentAddress(null);
        application.setQualifyingScore(0.0);
        application.setAdmissionLetterContent(null);
        application.setStatus(AdmissionApplication.ApplicationStatus.REVOKED);
        application.setRejectionReason("Offer Forcefully Revoked by Administration: " + revokeReason.trim());

        offerLetterRepository.findByApplication(application).ifPresent(offer -> {
            offer.setStatus(OfferLetter.OfferStatus.REVOKED);
            offerLetterRepository.saveAndFlush(offer);
        });
        AdmissionApplication revokedApplication = admissionRepository.saveAndFlush(application);

        // 🔔 AUTOMATIC NOTIFICATION
        if (studentUser != null) {
            eventPublisher.publishEvent(new NotificationDto.Event(
                    studentUser,
                    String.format("CRITICAL SECURITY ALARM: Your entry offer for %s has been forcefully revoked by the administration panel. Reason: %s", targetingProgram, revokeReason.trim()),
                    NotificationCategory.ADMISSIONS
            ));
        }

        log.info("Successfully enforced admin offer revocation state records for targets under applicationId: {}", applicationId);
        return revokedApplication;
    }

    // STAGE 5: VERIFY DOCUMENTS (ADMIN ACTIVITY)
    @Transactional(readOnly = true)
    public AdmissionApplication getDetailsForVerification(Long applicationId) {
        log.info("Entering getDetailsForVerification execution flow for applicationId: {}", applicationId);
        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        AdmissionApplication.ApplicationStatus currentStatus = application.getStatus();
        if (currentStatus != AdmissionApplication.ApplicationStatus.OFFER_ACCEPTED &&
                currentStatus != AdmissionApplication.ApplicationStatus.DOCUMENTS_VERIFIED &&
                currentStatus != AdmissionApplication.ApplicationStatus.ADMISSION_LETTER_ISSUED &&
                currentStatus != AdmissionApplication.ApplicationStatus.ENROLLED) {
            throw new AdmissionException("The applicant with ID " + applicationId + " has not accepted an offer or isn't cleared for verification status. Current status: " + currentStatus);
        }
        log.info("Successfully fetched details verification records framework mapping for applicationId: {}", applicationId);
        return application;
    }

    // STAGE 6: DOCUMENT VERIFICATION (ADMIN ACTIVITY)
    @Transactional
    public AdmissionApplication processDocumentVerification(Long applicationId, boolean isVerified) {
        log.info("Entering processDocumentVerification workflow logic map for applicationId: {} with verification clearing outcome: {}",
                applicationId, isVerified);

        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        if (application.getStatus() != AdmissionApplication.ApplicationStatus.OFFER_ACCEPTED) {
            throw new AdmissionException("Action Denied: No accepted offer records found available for validation scanning.");
        }

        // Cannot mark documents as verified unless the applicant has actually uploaded
        // all three required documents (10th, 12th, Aadhar). This prevents the header
        // from showing "DOCUMENTS VERIFIED" while individual documents are still PENDING.
        boolean allUploaded = application.getTenthMarksheetPath() != null
                && application.getTwelfthMarksheetPath() != null
                && application.getAadharCardPath() != null;

        if (isVerified) {
            if (!allUploaded) {
                throw new AdmissionException("Cannot verify: the applicant has not uploaded all required documents (10th marksheet, 12th marksheet, Aadhar card) yet.");
            }
            application.setStatus(AdmissionApplication.ApplicationStatus.DOCUMENTS_VERIFIED);
            application.setRejectionReason(null);
            application.setDocumentsVerified(true);
        } else {
            application.setStatus(AdmissionApplication.ApplicationStatus.REJECTED);
            application.setRejectionReason("The document verification is not completed due to false data provided. Kindly provide valid details within the 14 days expiration timeline.");
            application.setDocumentsVerified(false);
        }
        AdmissionApplication updatedApplication = admissionRepository.saveAndFlush(application);

        // 🔔 AUTOMATIC NOTIFICATION
        userRepository.findByEmail(authenticatedUserEmailLookupFallback(updatedApplication.getEmail())).ifPresent(student -> {
            String validationMessage = isVerified
                    ? "Admission Update: Your background document validation check completed successfully! Your records are now marked VERIFIED."
                    : "Admission Alert: Your document verification has failed due to conflicting data records provided.";
            eventPublisher.publishEvent(new NotificationDto.Event(student, validationMessage, NotificationCategory.ADMISSIONS));
        });

        log.info("Successfully concluded processDocumentVerification operation records mapping for applicationId: {}", applicationId);
        return updatedApplication;
    }

    // STAGE 7: ISSUE ADMISSION LETTER (ADMIN ACTIVITY)
    @Transactional
    public AdmissionApplication issueAdmissionLetter(Long applicationId) {
        log.info("Entering issueAdmissionLetter execution logic map for applicationId: {}", applicationId);
        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        if (application.getStatus() != AdmissionApplication.ApplicationStatus.DOCUMENTS_VERIFIED) {
            throw new AdmissionException("Please make sure your documents are verified. For further information, contact the admin.");
        }

        String onboardingContent = "Official University Dispatch: Congratulations " + application.getApplicantName() +
                "! Your entry verification checks cleared cleanly. Welcome aboard to CampusCore University System as a fully enrolled student.";
        application.setAdmissionLetterContent(onboardingContent);
        application.setStatus(AdmissionApplication.ApplicationStatus.ADMISSION_LETTER_ISSUED);
        AdmissionApplication updatedApplication = admissionRepository.saveAndFlush(application);

        // 🔔 AUTOMATIC NOTIFICATION
        userRepository.findByEmail(updatedApplication.getEmail()).ifPresent(student -> {
            eventPublisher.publishEvent(new NotificationDto.Event(
                    student,
                    "Admission Dispatch: Your official University Admission Letter has been generated. Please proceed to finalize your enrollment.",
                    NotificationCategory.ADMISSIONS
            ));
        });

        log.info("Successfully processed and stored dispatch system verification parameters for applicationId: {}", applicationId);
        return updatedApplication;
    }

    // STAGE 8: FINALIZE ENROLLMENT (STUDENT ACTIVITY)
    @Transactional
    public AdmissionApplication finalizeEnrollment(Long applicationId) {
        log.info("Entering finalizeEnrollment execution master dashboard logic flow for applicationId: {}", applicationId);
        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!application.getEmail().trim().equalsIgnoreCase(authenticatedEmail.trim())) {
            throw new AdmissionException("Access Denied: Operational error. Mismatched execution identity mapping context.");
        }
        if (application.getStatus() != AdmissionApplication.ApplicationStatus.ADMISSION_LETTER_ISSUED) {
            throw new AdmissionException("Action Denied: You cannot finalize enrollment until the university dispatches your checked admission letter.");
        }

        application.setStatus(AdmissionApplication.ApplicationStatus.ENROLLED);
        AdmissionApplication savedApplication = admissionRepository.saveAndFlush(application);

        User user = userRepository.findByEmail(application.getEmail())
                .orElseThrow(() -> new AdmissionException("Registered base user profile not found."));
        user.setRole(User.Role.STUDENT);
        user.setDepartmentId(application.getDepartmentId());
        userRepository.saveAndFlush(user);

        // 🔔 AUTOMATIC NOTIFICATION
        eventPublisher.publishEvent(new NotificationDto.Event(
                user,
                String.format("Welcome to CampusCore! Your final student enrollment onboarding sequence is complete. You are officially registered as a student under Department ID: %d.", savedApplication.getDepartmentId()),
                NotificationCategory.ADMISSIONS
        ));

        log.info("Successfully finalized master dashboard application configuration enrollment records for applicationId: {}", applicationId);
        return savedApplication;
    }

    // STAGE 9: QUERY APPLICATION STATUS (STUDENT ACTIVITY)
    @Transactional(readOnly = true)
    public AdmissionApplication.ApplicationStatus getApplicationStatus(Long applicationId) {
        log.info("Entering getApplicationStatus query processing state context for applicationId: {}", applicationId);
        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new AdmissionException("Access Denied: Logged-in user profile not found."));

        // Admins/registrars may view any application's status (mirrors the
        // verification-details and offer-details endpoints, which already allow admin).
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> {
                    String r = a.getAuthority();
                    return r.equals("ADMIN") || r.equals("ROLE_ADMIN")
                            || r.equals("REGISTRAR") || r.equals("ROLE_REGISTRAR");
                });

        if (!isAdmin
                && !application.getEmail().trim().equalsIgnoreCase(authenticatedEmail.trim())
                && !matchesUserContext(application, currentUser)) {
            throw new AdmissionException("Access Denied: You do not have permission to view this application's status.");
        }
        log.info("Successfully queried application current status updates mapping flow for id: {}", applicationId);
        return application.getStatus();
    }

    // STAGE 9B: LIGHTWEIGHT APPLICANT META (available at ANY stage, incl. SUBMITTED)
    // Returns the real applicant profile (name/email/program/etc.) so the admin detail
    // view never has to fall back to showing the logged-in admin's own name.
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getBasicInfo(Long applicationId) {
        log.info("Entering getBasicInfo query for applicationId: {}", applicationId);
        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> {
                    String r = a.getAuthority();
                    return r.equals("ADMIN") || r.equals("ROLE_ADMIN")
                            || r.equals("REGISTRAR") || r.equals("ROLE_REGISTRAR");
                });

        if (!isAdmin && !application.getEmail().trim().equalsIgnoreCase(authenticatedEmail.trim())) {
            throw new AdmissionException("Access Denied: You do not have permission to view this application's information.");
        }

        java.util.Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("applicationId", application.getApplicationId());
        info.put("applicantName", application.getApplicantName());
        info.put("email", application.getEmail());
        info.put("phone", application.getPhone());
        info.put("programName", application.getProgramName());
        info.put("programId", application.getProgramId());
        info.put("departmentName", application.getDepartmentName());
        info.put("departmentId", application.getDepartmentId());
        info.put("academicYear", application.getAcademicYear());
        info.put("qualifyingScore", application.getQualifyingScore());
        info.put("percentageSecured", application.getQualifyingScore());
        info.put("status", application.getStatus().name());
        log.info("Successfully fetched basic info for applicationId: {}", applicationId);
        return info;
    }

    // STAGE 10: FETCH OFFER LETTER (STUDENT ACTIVITY)
    @Transactional(readOnly = true)
    public OfferLetter getOfferLetterByApplicationId(Long applicationId) {
        log.info("Entering getOfferLetterByApplicationId mapping workflow lookup query index for applicationId: {}", applicationId);

        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new AdmissionException("Access Denied: Logged-in user profile not found."));

        if (!application.getEmail().trim().equalsIgnoreCase(authenticatedEmail.trim()) && !matchesUserContext(application, currentUser)) {
            throw new AdmissionException("Access Denied: You do not have permission to view this offer letter.");
        }

        OfferLetter trackingLetter = offerLetterRepository.findByApplication(application)
                .orElseGet(() -> offerLetterRepository.findById(applicationId)
                        .orElseThrow(() -> new AdmissionException("No active offer letter has been generated for application reference ID: " + applicationId)));

        log.info("Successfully located relational tracking target framework matching offer letter lookup for applicationId: {}", applicationId);
        return trackingLetter;
    }

    private boolean matchesUserContext(AdmissionApplication app, User user) {
        if (app.getFatherName() == null) return false;
        return app.getFatherName().equals(currentUserIdentificationHash(user));
    }

    private String currentUserIdentificationHash(User user) {
        if (user == null) return "anonymous_ctx";
        return "usr_ref_" + user.getUserId();
    }

    private String authenticatedUserEmailLookupFallback(String lookupKey) {
        if (lookupKey != null && (lookupKey.startsWith("withdrawn_") || lookupKey.startsWith("revoked_"))) {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        }
        return lookupKey;
    }

    // ==========================================
    // DOCUMENT UPLOAD & VERIFICATION (10th / 12th marksheet, Aadhar)
    // ==========================================

    private void verifyApplicantOwnsApplication(AdmissionApplication application) {
        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !application.getEmail().equalsIgnoreCase(authenticatedEmail)) {
            throw new AdmissionException("Access Denied: You do not have permission to access these documents.");
        }
    }

    @Transactional
    public AdmissionApplication uploadDocument(Long applicationId, String docType, MultipartFile file) {
        log.info("Processing document upload for applicationId: {}, docType: {}", applicationId, docType);
        String normalizedType = docType == null ? "" : docType.trim().toUpperCase();
        if (!ALLOWED_DOC_TYPES.contains(normalizedType)) {
            throw new AdmissionException("Invalid document type. Allowed values: TENTH, TWELFTH, AADHAR.");
        }

        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));

        verifyApplicantOwnsApplication(application);

        String storedPath = fileStorageService.store("admissions", String.valueOf(applicationId), file);

        switch (normalizedType) {
            case "TENTH" -> application.setTenthMarksheetPath(storedPath);
            case "TWELFTH" -> application.setTwelfthMarksheetPath(storedPath);
            case "AADHAR" -> application.setAadharCardPath(storedPath);
        }
        application.setDocumentsUploadedAt(LocalDateTime.now());
        // A fresh upload invalidates any previous verification decision
        application.setDocumentsVerified(false);

        AdmissionApplication saved = admissionRepository.saveAndFlush(application);
        log.info("Document '{}' stored successfully for applicationId: {}", normalizedType, applicationId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDocumentSummary(Long applicationId) {
        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));
        verifyApplicantOwnsApplication(application);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("applicationId", applicationId);
        summary.put("tenthMarksheetUploaded", application.getTenthMarksheetPath() != null);
        summary.put("twelfthMarksheetUploaded", application.getTwelfthMarksheetPath() != null);
        summary.put("aadharCardUploaded", application.getAadharCardPath() != null);
        summary.put("documentsUploadedAt", application.getDocumentsUploadedAt());
        summary.put("documentsVerified", application.isDocumentsVerified());
        return summary;
    }

    @Transactional(readOnly = true)
    public byte[] downloadDocument(Long applicationId, String docType) {
        String normalizedType = docType == null ? "" : docType.trim().toUpperCase();
        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));
        verifyApplicantOwnsApplication(application);

        String path = switch (normalizedType) {
            case "TENTH" -> application.getTenthMarksheetPath();
            case "TWELFTH" -> application.getTwelfthMarksheetPath();
            case "AADHAR" -> application.getAadharCardPath();
            default -> null;
        };
        if (path == null) {
            throw new AdmissionException("No document of type " + normalizedType + " has been uploaded for this application.");
        }
        return fileStorageService.read(path);
    }

    public String resolveDocumentContentType(Long applicationId, String docType) {
        AdmissionApplication application = admissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));
        String path = switch (docType == null ? "" : docType.trim().toUpperCase()) {
            case "TENTH" -> application.getTenthMarksheetPath();
            case "TWELFTH" -> application.getTwelfthMarksheetPath();
            case "AADHAR" -> application.getAadharCardPath();
            default -> null;
        };
        return path == null ? "application/octet-stream" : fileStorageService.contentTypeFor(path);
    }
}
 