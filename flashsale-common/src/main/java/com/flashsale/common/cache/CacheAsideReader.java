package com.flashsale.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis Cache Aside reader. Only the lock owner may call the database loader. */
@Component
public final class CacheAsideReader {
    private static final String LOCK_PREFIX = "cache:lock:";
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CacheAsideProperties properties;

    public CacheAsideReader(StringRedisTemplate redis, ObjectMapper objectMapper, CacheAsideProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public <T> CacheValue<T> read(String cacheKey, TypeReference<CacheValue<T>> type, Supplier<CacheValue<T>> loader) {
        CacheValue<T> cached = decode(cacheKey, redis.opsForValue().get(cacheKey), type);
        if (cached != null) return cached;

        String lockKey = LOCK_PREFIX + cacheKey;
        String owner = java.util.UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, owner, properties.getLockTtl());
        if (Boolean.TRUE.equals(acquired)) {
            try {
                cached = decode(cacheKey, redis.opsForValue().get(cacheKey), type);
                if (cached != null) return cached;
                CacheValue<T> loaded = loader.get();
                write(cacheKey, loaded);
                return loaded;
            } finally {
                redis.execute(UNLOCK, java.util.List.of(lockKey), owner);
            }
        }

        for (int attempt = 0; attempt < properties.getRetryCount(); attempt++) {
            sleep(properties.getRetryBackoff());
            cached = decode(cacheKey, redis.opsForValue().get(cacheKey), type);
            if (cached != null) return cached;
        }
        throw new CacheRebuildInProgressException(cacheKey);
    }

    private <T> void write(String key, CacheValue<T> value) {
        try {
            boolean negative = value.isError() || value.value() instanceof java.util.Collection<?> c && c.isEmpty();
            Duration base = negative ? properties.getNegativeTtl() : properties.getTtl();
            long jitter = negative ? 0 : randomJitter(properties.getTtlJitter());
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), base.plusMillis(jitter));
        } catch (Exception e) {
            throw new IllegalStateException("CACHE_SERIALIZATION_FAILED", e);
        }
    }

    private <T> CacheValue<T> decode(String key, String raw, TypeReference<CacheValue<T>> type) {
        if (raw == null) return null;
        try { return objectMapper.readValue(raw, type); }
        catch (Exception e) { redis.delete(key); return null; }
    }

    private static long randomJitter(Duration max) {
        long bound = max.toMillis();
        return bound <= 0 ? 0 : ThreadLocalRandom.current().nextLong(bound + 1);
    }

    private static void sleep(Duration duration) {
        try { Thread.sleep(Math.max(0, duration.toMillis())); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CacheRebuildInProgressException("interrupted"); }
    }
}
