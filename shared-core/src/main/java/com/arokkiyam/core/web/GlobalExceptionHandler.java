package com.arokkiyam.core.web;

import java.nio.file.AccessDeniedException;
import java.util.stream.Collectors;

import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.arokkiyam.core.exception.IdempotencyConflictException;
import com.arokkiyam.core.exception.TenantAccessDeniedException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler for all microservices.
 *
 * <p>Since shared-core is auto-configured, this handler is active in every
 * service without any additional configuration. Every controller exception
 * is caught here and mapped to a consistent {@link ApiResponse} shape.
 *
 * <p>Security principle: error responses never leak internal stack traces,
 * class names, or SQL details. Logs contain full details for debugging;
 * responses contain only safe, user-facing messages.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 400 Bad Request ───────────────────────────────────────

    /**
     * Handles @Valid / @Validated failures on @RequestBody.
     * Returns all field errors in a single response.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));

        String firstField = ex.getBindingResult().getFieldErrors()
            .stream()
            .findFirst()
            .map(FieldError::getField)
            .orElse(null);

        log.warn("Validation failed: {}", message);

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.fieldError("VALIDATION_FAILED", message, firstField));
    }

    /**
     * Handles @Validated failures on @PathVariable / @RequestParam.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex
    ) {
        String message = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", message);

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error("CONSTRAINT_VIOLATION", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(
            HttpMessageNotReadableException ex
    ) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error("INVALID_REQUEST_BODY",
                "Request body is missing or malformed"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {
        String message = String.format(
            "Parameter '%s' has invalid value '%s'",
            ex.getName(), ex.getValue()
        );
        log.warn("Type mismatch: {}", message);
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error("INVALID_PARAMETER", message));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException ex
    ) {
        String message = "Required header '" + ex.getHeaderName() + "' is missing";
        log.warn("Missing header: {}", ex.getHeaderName());
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error("MISSING_HEADER", message));
    }

    // ── 403 Forbidden ─────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex
    ) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("ACCESS_DENIED",
                "You do not have permission to perform this action"));
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantAccessDenied(
            TenantAccessDeniedException ex
    ) {
        // Log with tenant context for audit — but don't expose tenant details to caller
        log.warn("Tenant access denied: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("TENANT_ACCESS_DENIED",
                "Access to this resource is not permitted"));
    }

    // ── 404 Not Found ─────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            ResourceNotFoundException ex
    ) {
        log.info("Resource not found: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("NOT_FOUND", ex.getMessage()));
    }

    // ── 409 Conflict ──────────────────────────────────────────

    /**
     * Handles duplicate requests caught by IdempotencyFilter.
     * Returns the cached response directly — not a 409 error.
     * This handler is the fallback for cases the filter misses.
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(
            IdempotencyConflictException ex
    ) {
        log.info("Idempotent duplicate request detected: key={}",
            ex.getIdempotencyKey());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("DUPLICATE_REQUEST",
                "A request with this Idempotency-Key was already processed"));
    }

    // ── 500 Internal Server Error ─────────────────────────────

    /**
     * Catch-all for any unhandled exception.
     * Logs full stack trace internally — returns safe generic message externally.
     * Never expose internal details (class names, SQL, stack traces) to callers.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later."));
    }
}