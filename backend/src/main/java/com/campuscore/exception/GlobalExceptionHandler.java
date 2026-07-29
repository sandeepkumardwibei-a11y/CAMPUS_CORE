package com.campuscore.exception;
 
import com.campuscore.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.util.stream.Collectors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
 
import java.util.HashMap;
import java.util.Map;
 
/**
* Centralised exception handler that converts all known exceptions
* into the standard {@link ApiResponse} envelope.
*/
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
 
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }
 
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }
 
    /**
     *  UPDATE: Checks if the business message belongs to a suspended/inactive account
     * to dynamically elevate the response status code to 403 Forbidden.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        
        String msg = ex.getMessage();
        if (msg.contains("+91144889900") || msg.contains("withdrawn the application")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(msg));
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(msg));
    }
 
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        // Build one readable message out of the actual field errors (e.g. "Credits must be
        // between 1 and 8") instead of a generic "Validation failed" that hides the real reason.
        String combinedMessage = errors.values().stream()
                .filter(msg -> msg != null && !msg.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message(combinedMessage.isBlank() ? "Validation failed" : combinedMessage)
                        .data(errors)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }
 
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }
 
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }
 
    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<ApiResponse<String>> handleInvalidFormat(InvalidFormatException ex) {
        String target = ex.getTargetType() != null ? ex.getTargetType().getSimpleName() : "value";
        String allowed = "";
        try {
            Class<?> targetType = ex.getTargetType();
            if (targetType != null && targetType.isEnum()) {
                Object[] constants = targetType.getEnumConstants();
                allowed = java.util.Arrays.stream(constants).map(Object::toString).collect(Collectors.joining(", "));
            }
        } catch (Exception ignore) {}
 
        String message = String.format("Invalid value for %s. %s", target, allowed.isEmpty() ? "" : "Allowed values: " + allowed);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<String>builder().success(false).message(message).data(null).timestamp(java.time.LocalDateTime.now()).build());
    }
 
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied: you are not permitted to perform this action."));
    }
 
    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(org.springframework.security.authentication.DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Your registration is pending admin approval or may be your account has been deactivated."));
    }
 
    /**
     *  ADDITION: Handles native Spring Security account locked exceptions explicitly
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLocked(LockedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You had been removed by the admin of the college for your actions kindly contact to admin +91144889900 ."));
    }
 
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        // DEBUG AID: surface the real cause so the client popup shows what actually failed.
        // For production, revert the body below to a generic message to avoid leaking internals.
        String detail = ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : "");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Unexpected error — " + detail));
    }
}
 