package com.arokkiyam.core.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import com.arokkiyam.core.config.SharedCoreProperties;

/**
 * Configuration for idempotency services.
 */
@Configuration
@ConditionalOnClass(RedisTemplate.class)
public class IdempotencyConfig {

    /**
     * Configures the idempotency service.
     *
     * @param redisTemplate Redis template for storing idempotency keys
     * @param properties    shared core properties
     * @return configured idempotency service
     */
    @Bean
    public IdempotencyService idempotencyService(RedisTemplate<String, String> redisTemplate,
                                               SharedCoreProperties properties) {
        return new IdempotencyServiceImpl(redisTemplate, properties);
    }
}