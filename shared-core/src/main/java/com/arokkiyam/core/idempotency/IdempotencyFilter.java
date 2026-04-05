package com.arokkiyam.core.idempotency;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.arokkiyam.core.exception.IdempotencyConflictException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Filter that enforces idempotency for state-changing HTTP requests.
 *
 * <p>Checks for Idempotency-Key header on POST, PUT, PATCH, DELETE requests.
 * Excludes health checks and auth endpoints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private final IdempotencyService idempotencyService;

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String path = request.getRequestURI();

        // Skip if method doesn't require idempotency
        if (!idempotencyService.isMethodEnforced(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip if path is excluded
        if (idempotencyService.isPathExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check for idempotency key
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            log.warn("Missing Idempotency-Key header for {} {}", method, path);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Idempotency-Key header is required for this request\"}");
            return;
        }

        // Validate idempotency key format (basic UUID check)
        if (!isValidIdempotencyKey(idempotencyKey)) {
            log.warn("Invalid Idempotency-Key format: {}", idempotencyKey);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Idempotency-Key must be a valid UUID\"}");
            return;
        }

        try {
            // Check for duplicates
            idempotencyService.checkAndStore(idempotencyKey);
            log.debug("Idempotency check passed for key: {}", idempotencyKey);

            // Proceed with request
            filterChain.doFilter(request, response);

        } catch (IdempotencyConflictException e) {
            log.info("Duplicate request blocked: {}", e.getIdempotencyKey());
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().write("{\"error\":\"Duplicate request detected\"}");
        }
    }

    private boolean isValidIdempotencyKey(String key) {
        // Basic UUID validation
        return key.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }
}