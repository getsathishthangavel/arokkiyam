package com.arokkiyam.core.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration properties for shared-core.
 *
 * <p>Every microservice configures these in its own
 * {@code application.yml} under the {@code arokkiyam} prefix.
 *
 * <p>Example {@code application.yml}:
 * <pre>
 * arokkiyam:
 *   encryption:
 *     master-key: ${ENCRYPTION_MASTER_KEY}
 *   cache:
 *     default-ttl-seconds: 3600
 *     health-check-interval-seconds: 30
 *   idempotency:
 *     ttl-hours: 24
 *   tenant:
 *     header-name: X-Tenant-ID
 *   kafka:
 *     audit-topic: audit.event
 *     notification-topic: notification.send
 *     audit-dlq-topic: audit.event.dlq
 *     notification-dlq-topic: notification.send.dlq
 * </pre>
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "arokkiyam")
public class SharedCoreProperties {

    private final Encryption encryption = new Encryption();
    private final Cache cache = new Cache();
    private final Idempotency idempotency = new Idempotency();
    private final Tenant tenant = new Tenant();
    private final Kafka kafka = new Kafka();

    // ── Encryption ────────────────────────────────────────────

    @Getter
    @Setter
    public static class Encryption {

        /**
         * AES-256-GCM master key, base64-encoded.
         * Must be exactly 32 bytes when decoded.
         * Loaded from environment variable ENCRYPTION_MASTER_KEY.
         * Never hardcode — always inject via env var or Secrets Manager.
         */
        @NotBlank(message = "arokkiyam.encryption.master-key must not be blank")
        private String masterKey;
    }

    // ── Cache ─────────────────────────────────────────────────

    @Getter
    @Setter
    public static class Cache {

        /** Default Redis TTL in seconds. Default: 3600 (1 hour). */
        @Min(60)
        private long defaultTtlSeconds = 3600;

        /**
         * How often CacheWarmupJob polls Redis health.
         * When Redis recovers from DOWN, hot keys are re-synced.
         * Default: 30 seconds.
         */
        @Min(10)
        private long healthCheckIntervalSeconds = 30;

        /**
         * How many keys to warm per resync batch.
         * Prevents overloading MySQL on Redis recovery.
         * Default: 500.
         */
        @Min(1)
        private int warmupBatchSize = 500;
    }

    // ── Idempotency ───────────────────────────────────────────

    @Getter
    @Setter
    public static class Idempotency {

        /**
         * How long idempotency keys are retained.
         * After this period, duplicate requests are no longer
         * detected and will be processed normally.
         * Default: 24 hours.
         */
        @Min(1)
        private int ttlHours = 24;

        /**
         * HTTP methods that require idempotency key checks.
         * GET, HEAD, OPTIONS are safe — they don't mutate state.
         */
        private String[] enforcedMethods = {"POST", "PUT", "PATCH", "DELETE"};

        /**
         * URL path prefixes excluded from idempotency enforcement.
         * Health checks and auth endpoints are excluded.
         */
        private String[] excludedPaths = {
            "/actuator",
            "/auth/login",
            "/auth/refresh",
            "/.well-known"
        };
    }

    // ── Tenant ────────────────────────────────────────────────

    @Getter
    @Setter
    public static class Tenant {

        /**
         * HTTP header name carrying the tenant ID.
         * API Gateway injects this from the validated JWT claim.
         * Default: X-Tenant-ID
         */
        private String headerName = "X-Tenant-ID";

        /**
         * JWT claim name for tenant ID.
         * Must match what IAM service puts into the JWT payload.
         */
        private String jwtClaim = "tenant_id";
    }

    // ── Kafka ─────────────────────────────────────────────────

    @Getter
    @Setter
    public static class Kafka {

        @NotNull
        private String auditTopic = "audit.event";

        @NotNull
        private String notificationTopic = "notification.send";

        @NotNull
        private String auditDlqTopic = "audit.event.dlq";

        @NotNull
        private String notificationDlqTopic = "notification.send.dlq";

        /** Max delivery retries before routing to DLQ. */
        @Min(1)
        private int maxRetries = 3;

        /** Backoff between retries in milliseconds. */
        @Min(100)
        private long retryBackoffMs = 1000;
    }
}
