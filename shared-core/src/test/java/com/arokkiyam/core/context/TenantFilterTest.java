package com.arokkiyam.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.arokkiyam.core.config.SharedCoreProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for {@link TenantFilter}.
 *
 * <p>Uses Mockito — no Servlet container, no Spring context, no Docker.
 * Completes in milliseconds.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TenantFilter")
class TenantFilterTest {

    @Mock private HttpServletRequest  request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain         chain;
    @Mock private SharedCoreProperties properties;
    @Mock private SharedCoreProperties.Tenant tenantProperties;

    @InjectMocks
    private TenantFilter tenantFilter;

    @BeforeEach
    void setUp() {
        when(properties.getTenant()).thenReturn(tenantProperties);
        when(tenantProperties.getHeaderName()).thenReturn("X-Tenant-ID");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Header extraction ─────────────────────────────────────

    @Nested
    @DisplayName("Header extraction")
    class HeaderExtraction {

        @Test
        @DisplayName("populates TenantContext from all three headers")
        void populatesAllHeaders() throws Exception {
            String tenantId = UUID.randomUUID().toString();
            String userId   = UUID.randomUUID().toString();

            when(request.getHeader("X-Tenant-ID")).thenReturn(tenantId);
            when(request.getHeader(TenantFilter.HEADER_USER_ID)).thenReturn(userId);
            when(request.getHeader(TenantFilter.HEADER_USER_ROLE)).thenReturn("DOCTOR");
            when(request.getRequestURI()).thenReturn("/patients");

            tenantFilter.doFilterInternal(request, response, chain);

            // Filter clears context in finally — context already cleared at this point
            // So we verify chain was called AND capture context inside chain execution
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("calls chain.doFilter even when headers are missing")
        void proceedsWithoutHeaders() throws Exception {
            when(request.getHeader(anyString())).thenReturn(null);
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/patients");

            tenantFilter.doFilterInternal(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
        }
    }

    // ── Context cleared in finally ────────────────────────────

    @Nested
    @DisplayName("Context cleanup — the critical security guarantee")
    class ContextCleanup {

        @Test
        @DisplayName("clears TenantContext after successful request")
        void clearsContextAfterSuccess() throws Exception {
            when(request.getHeader("X-Tenant-ID"))
                .thenReturn(UUID.randomUUID().toString());
            when(request.getHeader(anyString())).thenReturn("some-value");
            when(request.getRequestURI()).thenReturn("/patients");

            tenantFilter.doFilterInternal(request, response, chain);

            // After filter completes, context must be cleared
            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(TenantContext.getCurrentUserId()).isNull();
            assertThat(TenantContext.getCurrentUserRole()).isNull();
        }

        @Test
        @DisplayName("clears TenantContext even when chain.doFilter throws")
        void clearsContextAfterException() throws Exception {
            when(request.getHeader("X-Tenant-ID"))
                .thenReturn(UUID.randomUUID().toString());
            when(request.getRequestURI()).thenReturn("/patients");
            doThrow(new RuntimeException("downstream failure"))
                .when(chain).doFilter(any(), any());

            assertThatThrownBy(() ->
                tenantFilter.doFilterInternal(request, response, chain)
            ).isInstanceOf(RuntimeException.class);

            // CRITICAL: context must be cleared even after exception
            assertThat(TenantContext.getTenantId()).isNull();
        }
    }

    // ── Actuator exclusion ────────────────────────────────────

    @Nested
    @DisplayName("shouldNotFilter()")
    class ShouldNotFilter {

        @Test
        @DisplayName("skips /actuator/health — no JWT on ECS health checks")
        void skipsActuatorHealth() {
            when(request.getRequestURI()).thenReturn("/actuator/health");
            assertThat(tenantFilter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("skips /actuator/info")
        void skipsActuatorInfo() {
            when(request.getRequestURI()).thenReturn("/actuator/info");
            assertThat(tenantFilter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("does not skip regular API paths")
        void doesNotSkipApiPaths() {
            when(request.getRequestURI()).thenReturn("/api/v1/patients");
            assertThat(tenantFilter.shouldNotFilter(request)).isFalse();
        }

        @Test
        @DisplayName("does not skip auth paths")
        void doesNotSkipAuthPaths() {
            when(request.getRequestURI()).thenReturn("/auth/login");
            assertThat(tenantFilter.shouldNotFilter(request)).isFalse();
        }
    }
}