package com.arokkiyam.core;

import com.arokkiyam.core.cache.ResilientCacheConfig;
import com.arokkiyam.core.config.JpaAuditingConfig;
import com.arokkiyam.core.config.KafkaConfig;
import com.arokkiyam.core.config.SharedCoreProperties;
import com.arokkiyam.core.context.TenantFilterConfig;
import com.arokkiyam.core.crypto.EncryptionConfig;
import com.arokkiyam.core.idempotency.IdempotencyConfig;
import com.arokkiyam.core.web.WebConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration for shared-core.
 *
 * <p>Every microservice that declares shared-core as a Maven
 * dependency automatically gets all cross-cutting concerns
 * registered as Spring beans — no manual @Import needed
 * in the service's own @SpringBootApplication class.
 *
 * <p>Registration happens via META-INF/spring/
 * org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * (Spring Boot 3.x mechanism replacing spring.factories).
 *
 * <p>Beans registered:
 * <ul>
 *   <li>TenantContext + TenantFilter (multi-tenancy)</li>
 *   <li>FieldEncryptor + EncryptedConverter (AES-256-GCM)</li>
 *   <li>ResilientCacheService (Redis + MySQL fallback)</li>
 *   <li>IdempotencyService + IdempotencyFilter</li>
 *   <li>GlobalExceptionHandler + ApiResponse</li>
 *   <li>AuditEventPublisher (Kafka producer)</li>
 *   <li>JPA auditing (@CreatedDate, @LastModifiedDate)</li>
 * </ul>
 */
@AutoConfiguration
@Import({
    SharedCoreProperties.class,
    JpaAuditingConfig.class,
    KafkaConfig.class,
    TenantFilterConfig.class,
    EncryptionConfig.class,
    ResilientCacheConfig.class,
    IdempotencyConfig.class,
    WebConfig.class
})
public class SharedCoreAutoConfiguration {
    // All bean registration is handled by the imported @Configuration classes.
    // This class is intentionally empty — it is the single entry point
    // that Spring Boot discovers via AutoConfiguration.imports.
}
