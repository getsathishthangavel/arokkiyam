package com.arokkiyam.core.exception;

import lombok.Getter;

/**
 * Thrown when a duplicate request is detected via the Idempotency-Key header.
 *
 * <p>The IdempotencyFilter intercepts this before it reaches controllers.
 * This exception is the fallback for edge cases the filter doesn't catch.
 *
 * <p>Maps to HTTP 409 Conflict in GlobalExceptionHandler.
 */
@Getter
public class IdempotencyConflictException extends RuntimeException {

    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey) {
        super("Duplicate request detected for idempotency key: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }
}