package com.arokkiyam.core.context;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local holder for the current request's tenant and user identity.
 *
 * <p>Populated by {@link TenantFilter} at the start of every HTTP request
 * from the validated JWT claims. Cleared at the end of every request to
 * prevent tenant context leaking between requests on pooled threads.
 *
 * <p>Usage in any service layer:
 * <pre>
 *   String tenantId = TenantContext.getRequiredTenantId();
 *   // throws if no tenant in context — safe default
 * </pre>
 *
 * <p>Usage in JPA repositories (via BaseEntity / @Query):
 * <pre>
 *   @Query("SELECT p FROM Patient p WHERE p.tenantId = :#{T(com.arokkiyam
 *           .core.context.TenantContext).getRequiredTenantId()}")
 * </pre>
 *
 * <p><strong>Thread safety:</strong> ThreadLocal is inherently thread-safe.
 * Each request thread gets its own isolated copy. Background threads
 * (scheduled jobs, async tasks) have no tenant context by design.
 */
@Slf4j
public final class TenantContext {

    // Separate ThreadLocals for each concern — keeps semantics clear
    private static final ThreadLocal<String> TENANT_ID  = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID    = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ROLE  = new ThreadLocal<>();

    // Utility class — no instantiation
    private TenantContext() {}

    // ── Setters (called only by TenantFilter) ─────────────────

    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static void setCurrentUserId(String userId) {
        USER_ID.set(userId);
    }

    public static void setCurrentUserRole(String role) {
        USER_ROLE.set(role);
    }

    // ── Getters ───────────────────────────────────────────────

    public static String getTenantId() {
        return TENANT_ID.get();
    }

    public static String getCurrentUserId() {
        return USER_ID.get();
    }

    public static String getCurrentUserRole() {
        return USER_ROLE.get();
    }

    /**
     * Returns the tenant ID or throws if not set.
     *
     * <p>Use this in service code where a missing tenant context
     * is a programming error — it means the filter was bypassed
     * or the request arrived without authentication.
     *
     * @throws IllegalStateException if no tenant context is set
     */
    public static String getRequiredTenantId() {
        String tenantId = TENANT_ID.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException(
                "No tenant context found on current thread. " +
                "Ensure TenantFilter is registered and the " +
                "request carries a valid JWT with tenant_id claim."
            );
        }
        return tenantId;
    }

    /**
     * Returns true if a tenant context is present on the current thread.
     * Use for conditional logic in shared code called from both
     * web requests and background jobs.
     */
    public static boolean hasTenantContext() {
        String tenantId = TENANT_ID.get();
        return tenantId != null && !tenantId.isBlank();
    }

    /**
     * Clears all thread-local state.
     *
     * <p><strong>Must</strong> be called in a finally block at the end
     * of every request. Failure to clear causes tenant context to leak
     * to the next request processed by the same thread — a serious
     * security vulnerability in a multi-tenant system.
     *
     * <p>Called automatically by {@link TenantFilter}.
     */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        USER_ROLE.remove();
        log.debug("TenantContext cleared for thread: {}",
            Thread.currentThread().getName());
    }
}