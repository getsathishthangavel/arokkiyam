package com.arokkiyam.core.audit;

import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.arokkiyam.core.config.SharedCoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka producer that publishes audit events to the {@code audit.event} topic.
 *
 * <p>Every service that extends {@code BaseEntity} uses this publisher.
 * It is auto-registered by shared-core's auto-configuration — services
 * inject it with {@code @Autowired} or constructor injection.
 *
 * <h2>Reliability design</h2>
 * <ul>
 *   <li>Fire-and-forget with callback logging — audit publishing never
 *       blocks the main business operation or causes it to fail.</li>
 *   <li>The Kafka producer is configured with {@code acks=all} and
 *       {@code enable.idempotence=true} (see KafkaConfig) — duplicate
 *       events are prevented at the producer level.</li>
 *   <li>If Kafka is temporarily unavailable, the send fails gracefully
 *       and is logged as a warning. The business operation succeeds.
 *       The Audit Service's DLQ handles redelivery.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * {@code
 * @Service
 * @RequiredArgsConstructor
 * public class PatientService {
 *
 *     private final AuditEventPublisher auditPublisher;
 *
 *     public Patient createPatient(CreatePatientRequest req) {
 *         Patient saved = patientRepository.save(patient);
 *
 *         auditPublisher.publish(AuditEventDto.builder()
 *             .tenantId(TenantContext.getRequiredTenantId())
 *             .actorId(TenantContext.getCurrentUserId())
 *             .action("PATIENT_CREATED")
 *             .entityType("Patient")
 *             .entityId(saved.getId().toString())
 *             .sourceService("patient-service")
 *             .build());
 *
 *         return saved;
 *     }
 * }
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SharedCoreProperties           properties;

    /**
     * Publishes an audit event asynchronously.
     *
     * <p>The Kafka message key is set to the {@code tenantId} — this
     * ensures all events for a single tenant are routed to the same
     * partition, preserving ordering within a tenant's audit trail.
     *
     * @param event the audit event to publish; must not be null
     */
    public void publish(AuditEventDto event) {
        if (event == null) {
            log.warn("Attempted to publish null audit event — skipping");
            return;
        }

        String topic = properties.getKafka().getAuditTopic();

        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(topic, event.getTenantId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error(
                    "Failed to publish audit event: action={}, entityType={}, " +
                    "entityId={}, tenantId={}, error={}",
                    event.getAction(),
                    event.getEntityType(),
                    event.getEntityId(),
                    event.getTenantId(),
                    ex.getMessage(),
                    ex
                );
            } else {
                log.debug(
                    "Audit event published: action={}, entityType={}, " +
                    "entityId={}, partition={}, offset={}",
                    event.getAction(),
                    event.getEntityType(),
                    event.getEntityId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
                );
            }
        });
    }

    /**
     * Publishes a simple audit event with minimal fields.
     * Convenience method for action-only events (e.g. LOGIN, LOGOUT).
     *
     * @param tenantId     the tenant context
     * @param actorId      the user performing the action
     * @param action       the action code e.g. "USER_LOGIN"
     * @param sourceService the emitting service name
     */
    public void publish(
            String tenantId,
            String actorId,
            String action,
            String sourceService
    ) {
        publish(AuditEventDto.builder()
            .tenantId(tenantId)
            .actorId(actorId)
            .action(action)
            .sourceService(sourceService)
            .build());
    }
}