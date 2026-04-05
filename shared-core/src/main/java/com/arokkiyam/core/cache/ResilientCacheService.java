package com.arokkiyam.core.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Resilient caching service with Redis primary and MySQL fallback.
 *
 * <p>Provides distributed caching with automatic failover:
 * - Primary: Redis (fast, in-memory)
 * - Fallback: MySQL (persistent, slower)
 *
 * <p>When Redis is down, operations fall back to MySQL seamlessly.
 * When Redis recovers, hot keys are automatically warmed up.
 */
public interface ResilientCacheService {

    /**
     * Stores a value in cache with default TTL.
     *
     * @param key   cache key
     * @param value value to store
     */
    void put(String key, Object value);

    /**
     * Stores a value in cache with custom TTL.
     *
     * @param key   cache key
     * @param value value to store
     * @param ttl   time to live
     */
    void put(String key, Object value, Duration ttl);

    /**
     * Retrieves a value from cache.
     *
     * @param key cache key
     * @return Optional containing value if present
     */
    <T> Optional<T> get(String key);

    /**
     * Removes a value from cache.
     *
     * @param key cache key
     */
    void evict(String key);

    /**
     * Checks if a key exists in cache.
     *
     * @param key cache key
     * @return true if key exists
     */
    boolean exists(String key);

    /**
     * Clears all cache entries.
     */
    void clear();
}