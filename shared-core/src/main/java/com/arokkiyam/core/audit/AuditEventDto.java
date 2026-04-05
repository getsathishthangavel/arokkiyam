package com.arokkiyam.core.audit;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable Kafka message payload for the {@code audit.event} topic.
 *
 * <p>Published by {@link AuditEventPublisher} whenever a state-changing
 * operation occurs in any service. Consumed exclusively by the Audit Service
 * which persists it to the append-only {@code audit_events} table.
 *
 * <h2>Mandatory fields</h2>
 * Every audit event must carry a tenantId, actorId, action, and entityType.
 * The payload map carries the before/after state or operation-specific detail.
 *
 * <h2>Example — patient created</h2>
 * <pre>
 * {@code
 * auditPublisher.publish(AuditEventDto.builder()
 *     .tenantId(TenantContext.getRequiredTenantId())
 *     .actorId(TenantContext.getCurrentUserId())
 *     .action("PATIENT_CREATED")
 *     .entityType("Patient")
 *     .entityId(patient.getId().toString())
 *     .payload(Map.of("name", patient.getName()))
 *     .build());
 * }
 * </pre>
 */
@Getter
@Builder
public class AuditEventDto {

    /** The clinic that owns this audit event. Never null. */
    private final String tenantId;

    /** The user ID who performed the action. "system" for jobs. */
    private final String actorId;

    /**
     * Action code — UPPER_SNAKE_CASE.
     * Examples: PATIENT_CREATED, PRESCRIPTION_UPDATED,
     *           USER_LOGIN, USER_LOGOUT, INVOICE_PAID.
     */
    private final String action;

    /**
     * The type of entity affected.
     * Examples: Patient, User, Tenant, Prescription, Invoice.
     */
    private final String entityType;

    /**
     * The ID of the affected entity as a UUID string.
     * Null for actions not tied to a specific entity (e.g. LOGIN).
     */
    private final String entityId;

    /**
     * Free-form payload for operation-specific context.
     * For CREATE: new field values.
     * For UPDATE: changed fields with before/after values.
     * For DELETE: the deleted record's key fields.
     *
     * <p>Sensitive fields (phone, diagnosis) must NOT be included.
     * Audit events are not encrypted — they are stored as plain JSON.
     */
    private final Map<String, Object> payload;

    /**
     * UTC timestamp of the event. Auto-set to now() if not provided.
     * Explicitly settable for event replay scenarios.
     */
    @Builder.Default
    private final Instant occurredAt = Instant.now();

    /**
     * The service that emitted this event.
     * e.g. "iam-service", "patient-service".
     * Used for debugging and traceability.
     */
    private final String sourceService;
}