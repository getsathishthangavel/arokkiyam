package com.arokkiyam.core.config;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.arokkiyam.core.context.TenantContext;

/**
 * Enables Spring Data JPA auditing for all entities that extend BaseEntity.
 *
 * <p>This wires up:
 * <ul>
 *   <li>{@code @CreatedDate} — auto-set to current UTC instant on INSERT</li>
 *   <li>{@code @LastModifiedDate} — auto-updated on every UPDATE</li>
 *   <li>{@code @CreatedBy} — auto-set to the current authenticated user ID</li>
 *   <li>{@code @LastModifiedBy} — auto-updated to the modifier's user ID</li>
 * </ul>
 *
 * <p>No entity needs to call setCreatedAt() manually — JPA handles it.
 */
@Configuration
@EnableJpaAuditing(
    auditorAwareRef  = "auditorProvider",
    dateTimeProviderRef = "utcDateTimeProvider"
)
public class JpaAuditingConfig {

    /**
     * Provides the current user ID for @CreatedBy / @LastModifiedBy.
     *
     * <p>Reads from TenantContext which is populated by TenantFilter
     * from the validated JWT on every request. Falls back to "system"
     * for background jobs and scheduled tasks that have no user context.
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(
            TenantContext.getCurrentUserId()
        ).or(() -> Optional.of("system"));
    }

    /**
     * Forces all audit timestamps to UTC.
     *
     * <p>Critical for a multi-tenant healthcare platform —
     * clinics may be in different timezones, but all audit
     * records must be stored and compared in UTC.
     */
    @Bean
    public DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}