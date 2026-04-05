package com.arokkiyam.core.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.arokkiyam.core.config.SharedCoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resilient cache implementation with Redis primary and MySQL fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientCacheServiceImpl implements ResilientCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final SharedCoreProperties properties;
    private final ObjectMapper objectMapper;

    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS cache_entries (
            cache_key VARCHAR(255) PRIMARY KEY,
            cache_value LONGTEXT NOT NULL,
            expires_at TIMESTAMP NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """;

    private static final String UPSERT_SQL = """
        INSERT INTO cache_entries (cache_key, cache_value, expires_at)
        VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? SECOND))
        ON DUPLICATE KEY UPDATE
            cache_value = VALUES(cache_value),
            expires_at = VALUES(expires_at)
        """;

    private static final String SELECT_SQL = """
        SELECT cache_value FROM cache_entries
        WHERE cache_key = ? AND expires_at > NOW()
        """;

    private static final String DELETE_SQL = """
        DELETE FROM cache_entries WHERE cache_key = ?
        """;

    private static final String EXISTS_SQL = """
        SELECT 1 FROM cache_entries
        WHERE cache_key = ? AND expires_at > NOW()
        LIMIT 1
        """;

    private static final String CLEAR_SQL = """
        DELETE FROM cache_entries
        """;

    private static final String CLEANUP_SQL = """
        DELETE FROM cache_entries WHERE expires_at <= NOW()
        """;

    @PostConstruct
    public void initialize() {
        initializeMySqlTable();
    }

    private void initializeMySqlTable() {
        try {
            jdbcTemplate.execute(CREATE_TABLE_SQL);
            log.info("MySQL cache table initialized");
        } catch (Exception e) {
            log.error("Failed to initialize MySQL cache table", e);
        }
    }

    @Override
    public void put(String key, Object value) {
        put(key, value, Duration.ofSeconds(properties.getCache().getDefaultTtlSeconds()));
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        String serializedValue = serialize(value);

        try {
            // Try Redis first
            redisTemplate.opsForValue().set(key, serializedValue, ttl);
            log.debug("Cached in Redis: key={}", key);
        } catch (Exception e) {
            log.warn("Redis unavailable, falling back to MySQL for key={}", key, e);
            // Fallback to MySQL
            try {
                jdbcTemplate.update(UPSERT_SQL, key, serializedValue, ttl.getSeconds());
                log.debug("Cached in MySQL: key={}", key);
            } catch (Exception mysqlEx) {
                log.error("Failed to cache in MySQL for key={}", key, mysqlEx);
            }
        }
    }

    @Override
    public <T> Optional<T> get(String key) {
        try {
            // Try Redis first
            String redisValue = redisTemplate.opsForValue().get(key);
            if (redisValue != null) {
                log.debug("Retrieved from Redis: key={}", key);
                return Optional.of(deserialize(redisValue));
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, falling back to MySQL for key={}", key, e);
        }

        // Fallback to MySQL
        try {
            String mysqlValue = jdbcTemplate.queryForObject(SELECT_SQL, String.class, key);
            if (mysqlValue != null) {
                log.debug("Retrieved from MySQL: key={}", key);
                return Optional.of(deserialize(mysqlValue));
            }
        } catch (Exception e) {
            log.debug("Key not found in MySQL: key={}", key);
        }

        return Optional.empty();
    }

    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis unavailable during evict", e);
        }

        try {
            jdbcTemplate.update(DELETE_SQL, key);
        } catch (Exception e) {
            log.warn("MySQL unavailable during evict", e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            Boolean redisExists = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(redisExists)) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable during exists check", e);
        }

        try {
            Integer count = jdbcTemplate.queryForObject(EXISTS_SQL, Integer.class, key);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("Key not found in MySQL during exists check: key={}", key);
            return false;
        }
    }

    @Override
    public void clear() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
        } catch (Exception e) {
            log.warn("Redis unavailable during clear", e);
        }

        try {
            jdbcTemplate.update(CLEAR_SQL);
        } catch (Exception e) {
            log.warn("MySQL unavailable during clear", e);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize value", e);
            throw new RuntimeException("Serialization failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T deserialize(String value) {
        try {
            return (T) objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize value", e);
            throw new RuntimeException("Deserialization failed", e);
        }
    }
}