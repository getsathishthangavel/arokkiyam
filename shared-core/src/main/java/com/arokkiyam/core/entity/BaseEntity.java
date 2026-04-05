package com.arokkiyam.core.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.arokkiyam.core.context.TenantContext;
import com.arokkiyam.core.util.UuidV7Util;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;

/**
 * Base class for every JPA entity in every Arokkiyam service.
 *
 * <h2>What every entity inherits</h2>
 * <ul>
 *   <li><strong>id</strong> — UUID v7, stored as {@code BINARY(16)}.
 *       Time-sorted so MySQL B-tree index stays sequential.
 *       Generated before INSERT via {@code @PrePersist} — the value
 *       is available in the application immediately, no round-trip needed.</li>
 *
 *   <li><strong>tenantId</strong> — the clinic/tenant that owns this row.
 *       Auto-populated from {@link TenantContext} in {@code @PrePersist}.
 *       Never null — every row is owned by exactly one tenant.
 *       Enforced at the DB level via {@code NOT NULL} on every table.</li>
 *
 *   <li><strong>createdAt / updatedAt</strong> — UTC timestamps managed
 *       by Spring Data JPA auditing. No service code touches these.</li>
 *
 *   <li><strong>createdBy / updatedBy</strong> — the user ID of the actor,
 *       set from {@link TenantContext} via the {@code AuditorAware} bean
 *       in {@code JpaAuditingConfig}. Background jobs get "system".</li>
 * </ul>
 *
 * <h2>Multi-tenancy enforcement</h2>
 * {@code tenantId} is set once in {@code @PrePersist} and never
 * updated thereafter. Repository queries must always include
 * {@code WHERE tenant_id = ?} — enforced via Spring Data query methods
 * and {@code @Query} annotations in each service's repository.
 *
 * <h2>Usage</h2>
 * <pre>
 * {@code
 * @Entity
 * @Table(name = "patients")
 * public class Patient extends BaseEntity {
 *
 *     @Encrypted
 *     @Convert(converter = EncryptedConverter.class)
 *     private String phoneNumber;
 *
 *     private String name;
 * }
 * }
 * </pre>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /**
     * Primary key — UUID v7, stored as BINARY(16).
     *
     * <p>Strategy: {@code GenerationType.NONE} — the ID is assigned
     * by the application in {@code @PrePersist}, not by the database.
     * This allows the ID to be known immediately (e.g. for Kafka event
     * publishing) without a database round-trip.
     *
     * <p>The {@code UuidV7AttributeConverter} (autoApply=true) handles
     * the UUID ↔ byte[] conversion transparently.
     */
    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", nullable = false,
            updatable = false)
    private UUID id;

    /**
     * Tenant identifier — the clinic that owns this row.
     *
     * <p>Auto-populated from {@link TenantContext#getRequiredTenantId()}
     * in {@code @PrePersist}. Never null, never updated.
     *
     * <p>Every repository query must filter by this value.
     * Example:
     * <pre>
     *   Optional<Patient> findByIdAndTenantId(UUID id, UUID tenantId);
     * </pre>
     */
    @Column(name = "tenant_id", columnDefinition = "BINARY(16)",
            nullable = false, updatable = false)
    private UUID tenantId;

    /**
     * UTC timestamp of first INSERT. Managed by Spring Data auditing.
     * Never updated after creation.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * UTC timestamp of most recent UPDATE. Managed by Spring Data auditing.
     * Updated automatically on every entity save.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * User ID of the actor who created this record.
     * Populated from TenantContext via AuditorAware.
     * "system" for background jobs.
     */
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false,
            length = 36)
    private String createdBy;

    /**
     * User ID of the actor who last modified this record.
     * Updated on every save.
     */
    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 36)
    private String updatedBy;

    // ── Lifecycle callbacks ───────────────────────────────────

    /**
     * Called by JPA immediately before INSERT.
     *
     * <p>Assigns:
     * <ul>
     *   <li>A new UUID v7 as the primary key</li>
     *   <li>The current tenant ID from TenantContext</li>
     * </ul>
     *
     * <p>Both values are permanent — {@code updatable = false} on
     * both columns prevents any UPDATE from touching them.
     */
    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UuidV7Util.generate();
        }
        if (this.tenantId == null) {
            this.tenantId = UUID.fromString(
                TenantContext.getRequiredTenantId()
            );
        }
    }

    // ── Test helpers ──────────────────────────────────────────

    /**
     * Protected setter for test classes to set ID.
     * Only used in unit tests to simulate database loading.
     */
    protected void setId(UUID id) {
        this.id = id;
    }

    /**
     * Protected setter for test classes to set tenantId.
     * Only used in unit tests to simulate database loading.
     */
    protected void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    // ── Equality ─────────────────────────────────────────────

    /**
     * Entities are equal if they have the same non-null ID.
     *
     * <p>Follows the JPA best-practice of not using generated IDs
     * in equals/hashCode before the entity is persisted. Two transient
     * entities (id == null) are never considered equal.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        // Constant hash for unpersisted entities — safe for Set/Map
        // before and after persistence.
        return id != null ? id.hashCode() : getClass().hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
               "[id=" + id + ", tenantId=" + tenantId + "]";
    }
}