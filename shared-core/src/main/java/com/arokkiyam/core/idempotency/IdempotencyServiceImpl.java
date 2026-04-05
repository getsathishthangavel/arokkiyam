package com.arokkiyam.core.idempotency;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.arokkiyam.core.config.SharedCoreProperties;
import com.arokkiyam.core.exception.IdempotencyConflictException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis-based idempotency service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SharedCoreProperties properties;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";

    @Override
    public Optional<String> checkAndStore(String key) {
        String redisKey = IDEMPOTENCY_PREFIX + key;

        try {
            // Check if key exists
            String existingResponse = redisTemplate.opsForValue().get(redisKey);
            if (existingResponse != null) {
                log.info("Duplicate request detected for key: {}", key);
                throw new IdempotencyConflictException(key);
            }

            // Store processing marker with short TTL
            redisTemplate.opsForValue().set(
                redisKey,
                "PROCESSING",
                Duration.ofMinutes(5) // Short TTL for processing state
            );

            return Optional.empty();
        } catch (IdempotencyConflictException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis unavailable during idempotency check for key: {}", key, e);
            // In case of Redis failure, allow request to proceed (fail open)
            return Optional.empty();
        }
    }

    @Override
    public void storeResponse(String key, String response) {
        String redisKey = IDEMPOTENCY_PREFIX + key;

        try {
            Duration ttl = Duration.ofHours(properties.getIdempotency().getTtlHours());
            redisTemplate.opsForValue().set(redisKey, response, ttl);
            log.debug("Stored idempotency response for key: {}", key);
        } catch (Exception e) {
            log.warn("Failed to store idempotency response for key: {}", key, e);
        }
    }

    @Override
    public void remove(String key) {
        String redisKey = IDEMPOTENCY_PREFIX + key;

        try {
            redisTemplate.delete(redisKey);
            log.debug("Removed idempotency key: {}", key);
        } catch (Exception e) {
            log.warn("Failed to remove idempotency key: {}", key, e);
        }
    }

    @Override
    public boolean isMethodEnforced(String method) {
        return Arrays.asList(properties.getIdempotency().getEnforcedMethods())
                    .contains(method.toUpperCase());
    }

    @Override
    public boolean isPathExcluded(String path) {
        return Arrays.stream(properties.getIdempotency().getExcludedPaths())
                    .anyMatch(excluded -> path.startsWith(excluded));
    }
}