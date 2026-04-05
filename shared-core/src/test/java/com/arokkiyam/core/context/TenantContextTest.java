package com.arokkiyam.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TenantContext}.
 *
 * <p>Critical tests include ThreadLocal isolation between threads —
 * a regression here would be a multi-tenant security vulnerability.
 */
@Tag("unit")
@DisplayName("TenantContext")
class TenantContextTest {

    @AfterEach
    void clearAfterEach() {
        // Always clean up — tests run on the same thread
        TenantContext.clear();
    }

    // ── Basic get/set ─────────────────────────────────────────

    @Nested
    @DisplayName("set and get")
    class SetAndGet {

        @Test
        @DisplayName("stores and retrieves tenantId correctly")
        void storesAndRetrievesTenantId() {
            String tenantId = UUID.randomUUID().toString();
            TenantContext.setTenantId(tenantId);

            assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
        }

        @Test
        @DisplayName("stores and retrieves userId correctly")
        void storesAndRetrievesUserId() {
            String userId = UUID.randomUUID().toString();
            TenantContext.setCurrentUserId(userId);

            assertThat(TenantContext.getCurrentUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("stores and retrieves user role correctly")
        void storesAndRetrievesUserRole() {
            TenantContext.setCurrentUserRole("DOCTOR");
            assertThat(TenantContext.getCurrentUserRole()).isEqualTo("DOCTOR");
        }

        @Test
        @DisplayName("returns null for all fields when context not set")
        void returnsNullWhenNotSet() {
            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(TenantContext.getCurrentUserId()).isNull();
            assertThat(TenantContext.getCurrentUserRole()).isNull();
        }
    }

    // ── hasTenantContext() ────────────────────────────────────

    @Nested
    @DisplayName("hasTenantContext()")
    class HasTenantContext {

        @Test
        @DisplayName("returns false when no tenant set")
        void falseWhenNotSet() {
            assertThat(TenantContext.hasTenantContext()).isFalse();
        }

        @Test
        @DisplayName("returns true when tenant is set")
        void trueWhenSet() {
            TenantContext.setTenantId(UUID.randomUUID().toString());
            assertThat(TenantContext.hasTenantContext()).isTrue();
        }

        @Test
        @DisplayName("returns false for blank tenant ID")
        void falseForBlankTenantId() {
            TenantContext.setTenantId("   ");
            assertThat(TenantContext.hasTenantContext()).isFalse();
        }
    }

    // ── getRequiredTenantId() ─────────────────────────────────

    @Nested
    @DisplayName("getRequiredTenantId()")
    class GetRequired {

        @Test
        @DisplayName("returns tenant ID when set")
        void returnsWhenSet() {
            String tenantId = UUID.randomUUID().toString();
            TenantContext.setTenantId(tenantId);

            assertThat(TenantContext.getRequiredTenantId()).isEqualTo(tenantId);
        }

        @Test
        @DisplayName("throws IllegalStateException when tenant not set")
        void throwsWhenNotSet() {
            assertThatThrownBy(TenantContext::getRequiredTenantId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant context");
        }

        @Test
        @DisplayName("throws when tenant ID is blank")
        void throwsWhenBlank() {
            TenantContext.setTenantId("  ");
            assertThatThrownBy(TenantContext::getRequiredTenantId)
                .isInstanceOf(IllegalStateException.class);
        }
    }

    // ── clear() ───────────────────────────────────────────────

    @Nested
    @DisplayName("clear()")
    class Clear {

        @Test
        @DisplayName("clears all three ThreadLocal values")
        void clearsAllValues() {
            TenantContext.setTenantId(UUID.randomUUID().toString());
            TenantContext.setCurrentUserId(UUID.randomUUID().toString());
            TenantContext.setCurrentUserRole("ADMIN");

            TenantContext.clear();

            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(TenantContext.getCurrentUserId()).isNull();
            assertThat(TenantContext.getCurrentUserRole()).isNull();
        }

        @Test
        @DisplayName("clear() on empty context does not throw")
        void clearOnEmptyContextIsIdempotent() {
            assertThatNoException().isThrownBy(TenantContext::clear);
        }
    }

    // ── Thread isolation — the critical security test ─────────

    @Nested
    @DisplayName("ThreadLocal isolation between threads")
    class ThreadIsolation {

        @Test
        @DisplayName("each thread has its own isolated tenant context")
        void threadsHaveIsolatedContexts() throws Exception {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();

            // Each thread sets its own unique tenant ID, waits for all threads
            // to start, then reads back what it set
            for (int i = 0; i < threadCount; i++) {
                String tenantId = "tenant-" + i;
                futures.add(executor.submit(() -> {
                    try {
                        TenantContext.setTenantId(tenantId);
                        startLatch.await(); // all threads read at the same time
                        return TenantContext.getTenantId();
                    } finally {
                        TenantContext.clear();
                    }
                }));
            }

            startLatch.countDown(); // release all threads simultaneously

            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get());
            }

            executor.shutdown();

            // Every thread must read exactly its own tenant ID — no cross-contamination
            assertThat(results)
                .hasSize(threadCount)
                .allSatisfy(result ->
                    assertThat(result).startsWith("tenant-")
                );

            // All tenant IDs should be present (each thread got its own)
            assertThat(new java.util.HashSet<>(results)).hasSize(threadCount);
        }

        @Test
        @DisplayName("context on main thread does not leak to child thread")
        void mainThreadContextDoesNotLeakToChildThread() throws Exception {
            TenantContext.setTenantId("main-tenant");

            Future<String> childResult = Executors.newSingleThreadExecutor()
                .submit(TenantContext::getTenantId);

            String childTenantId = childResult.get();

            // Child thread must NOT see parent thread's context
            assertThat(childTenantId).isNull();
        }
    }
}