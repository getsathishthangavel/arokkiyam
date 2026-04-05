package com.arokkiyam.core.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.arokkiyam.core.config.SharedCoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Configuration for resilient caching with Redis primary and MySQL fallback.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass({RedisTemplate.class, JdbcTemplate.class})
public class ResilientCacheConfig {

    private final SharedCoreProperties properties;

    /**
     * Configures the resilient cache service.
     *
     * @param redisTemplate Redis template for primary caching
     * @param jdbcTemplate  JDBC template for fallback caching
     * @param objectMapper  Jackson object mapper for serialization
     * @return configured cache service
     */
    @Bean
    public ResilientCacheService resilientCacheService(
            RedisTemplate<String, String> redisTemplate,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        return new ResilientCacheServiceImpl(redisTemplate, jdbcTemplate, properties, objectMapper);
    }
}