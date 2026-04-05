package com.arokkiyam.core.idempotency;

import java.util.Optional;

import com.arokkiyam.core.exception.IdempotencyConflictException;

/**
 * Service for handling idempotent operations.
 *
 * <p>Prevents duplicate processing of requests by tracking
 * idempotency keys with their associated responses.
 */
public interface IdempotencyService {

    /**
     * Checks if a request with the given key has already been processed.
     *
     * @param key idempotency key
     * @return Optional containing the cached response if duplicate
     * @throws IdempotencyConflictException if duplicate detected
     */
    Optional<String> checkAndStore(String key);

    /**
     * Stores the response for a successfully processed request.
     *
     * @param key      idempotency key
     * @param response response to cache
     */
    void storeResponse(String key, String response);

    /**
     * Removes the idempotency key (for cleanup or error cases).
     *
     * @param key idempotency key to remove
     */
    void remove(String key);

    /**
     * Checks if the given HTTP method requires idempotency enforcement.
     *
     * @param method HTTP method
     * @return true if method requires idempotency
     */
    boolean isMethodEnforced(String method);

    /**
     * Checks if the given path is excluded from idempotency enforcement.
     *
     * @param path request path
     * @return true if path is excluded
     */
    boolean isPathExcluded(String path);
}