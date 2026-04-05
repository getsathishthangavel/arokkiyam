package com.arokkiyam.core.web;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

/**
 * Standard response envelope for every API endpoint across all services.
 *
 * <p>Every controller in every microservice returns
 * {@code ResponseEntity<ApiResponse<T>>}. This ensures the frontend
 * always receives a consistent shape regardless of which service
 * responded, which simplifies TanStack Query data selectors.
 *
 * <p>Success shape:
 * <pre>
 * {
 *   "success": true,
 *   "data": { ... },
 *   "timestamp": "2025-01-15T10:30:00Z"
 * }
 * </pre>
 *
 * <p>Error shape (error field present, data absent):
 * <pre>
 * {
 *   "success": false,
 *   "error": {
 *     "code": "TENANT_NOT_FOUND",
 *     "message": "Clinic with ID xyz does not exist",
 *     "field": null
 *   },
 *   "timestamp": "2025-01-15T10:30:00Z"
 * }
 * </pre>
 *
 * @param <T> the type of the {@code data} payload
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ApiError error;
    private final Instant timestamp;

    // Private — use static factory methods
    private ApiResponse(boolean success, T data, ApiError error) {
        this.success   = success;
        this.data      = data;
        this.error     = error;
        this.timestamp = Instant.now();
    }

    // ── Factory methods ───────────────────────────────────────

    /**
     * Creates a successful response with a data payload.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * Creates a successful response with no data payload.
     * Use for 204 No Content equivalent responses.
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * Creates an error response with a structured error object.
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null,
            new ApiError(code, message, null));
    }

    /**
     * Creates a field-level validation error response.
     */
    public static <T> ApiResponse<T> fieldError(
            String code, String message, String field
    ) {
        return new ApiResponse<>(false, null,
            new ApiError(code, message, field));
    }

    // ── Nested error shape ────────────────────────────────────

    /**
     * Structured error object within the response envelope.
     * {@code field} is only present for validation errors.
     */
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiError {

        /** Machine-readable error code. e.g. TENANT_NOT_FOUND */
        private final String code;

        /** Human-readable message safe to display in UI. */
        private final String message;

        /** Populated for field-level validation errors only. */
        private final String field;

        ApiError(String code, String message, String field) {
            this.code    = code;
            this.message = message;
            this.field   = field;
        }
    }
}