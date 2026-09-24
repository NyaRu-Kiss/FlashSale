package com.flashsale.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;

/** Best-effort, idempotent cache invalidation. Cache is never repopulated here. */
public final class CacheInvalidator {
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidator.class);
    private final StringRedisTemplate redis;

    public CacheInvalidator(StringRedisTemplate redis) { this.redis = redis; }

    public void invalidate(String... keys) {
        if (keys == null || keys.length == 0) return;
        try { redis.delete(Arrays.asList(keys)); }
        catch (RuntimeException ex) { log.warn("cache invalidation failed keys={}", Arrays.toString(keys), ex); }
    }
}
