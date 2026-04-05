package com.arokkiyam.core.exception;

/**
 * Thrown when a request attempts to access data belonging to
 * a different tenant than the one in the current TenantContext.
 *
 * <p>This is a security boundary violation. Maps to HTTP 403.
 * The response deliberately does not expose which tenant owns
 * the resource — that would be an information leak.
 */
public class TenantAccessDeniedException extends RuntimeException {

    public TenantAccessDeniedException(String message) {
        super(message);
    }

    public TenantAccessDeniedException(
            String resourceType,
            Object resourceId,
            String requestingTenantId
    ) {
        super(String.format(
            "Tenant '%s' attempted to access %s '%s' belonging to a different tenant",
            requestingTenantId, resourceType, resourceId
        ));
    }
}