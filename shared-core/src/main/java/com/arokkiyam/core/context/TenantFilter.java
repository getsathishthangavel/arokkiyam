package com.arokkiyam.core.context;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.arokkiyam.core.config.SharedCoreProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servlet filter that populates {@link TenantContext} from HTTP headers
 * injected by AWS API Gateway after JWT validation.
 *
 * <p>Flow:
 * <ol>
 *   <li>API Gateway validates the Bearer JWT using the IAM Service JWKS endpoint</li>
 *   <li>On successful validation, API Gateway forwards the request with
 *       additional headers derived from JWT claims:
 *       <ul>
 *         <li>{@code X-Tenant-ID} — from {@code tenant_id} JWT claim</li>
 *         <li>{@code X-User-ID}   — from {@code sub} JWT claim</li>
 *         <li>{@code X-User-Role} — from {@code role} JWT claim</li>
 *       </ul>
 *   </li>
 *   <li>This filter reads those headers and populates TenantContext</li>
 *   <li>All downstream service code reads TenantContext — never the raw JWT</li>
 *   <li>TenantContext is cleared in {@code finally} to prevent thread leak</li>
 * </ol>
 *
 * <p>Ordering: {@code @Order(1)} — runs before all other filters,
 * ensuring tenant context is available to Spring Security and controllers.
 *
 * <p>Health check paths ({@code /actuator/**}) are excluded — they carry
 * no JWT and need no tenant context.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    // Headers injected by AWS API Gateway after JWT validation
    static final String HEADER_USER_ID   = "X-User-ID";
    static final String HEADER_USER_ROLE = "X-User-Role";

    private final SharedCoreProperties properties;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         chain
    ) throws ServletException, IOException {

        try {
            String tenantId  = request.getHeader(
                properties.getTenant().getHeaderName()
            );
            String userId    = request.getHeader(HEADER_USER_ID);
            String userRole  = request.getHeader(HEADER_USER_ROLE);

            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.setTenantId(tenantId);
                log.debug("Tenant context set: tenantId={}, userId={}, role={}",
                    tenantId, userId, userRole);
            } else {
                log.debug("No tenant header on request: {} {}",
                    request.getMethod(), request.getRequestURI());
            }

            if (userId != null && !userId.isBlank()) {
                TenantContext.setCurrentUserId(userId);
            }
            if (userRole != null && !userRole.isBlank()) {
                TenantContext.setCurrentUserRole(userRole);
            }

            chain.doFilter(request, response);

        } finally {
            // CRITICAL: always clear — prevents tenant context leaking
            // to the next request on this pooled thread.
            TenantContext.clear();
        }
    }

    /**
     * Skip tenant filter for actuator health checks.
     * These endpoints are called by ECS and ALB — no JWT present.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}