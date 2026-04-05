package com.arokkiyam.core.exception;

/**
 * Thrown when a requested resource does not exist.
 *
 * <p>Usage:
 * <pre>
 *   Patient patient = patientRepository.findById(id)
 *       .orElseThrow(() -> new ResourceNotFoundException(
 *           "Patient not found with ID: " + id));
 * </pre>
 *
 * Maps to HTTP 404 in GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, Object id) {
        super(resourceType + " not found with ID: " + id);
    }
}