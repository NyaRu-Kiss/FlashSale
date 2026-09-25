package com.flashsale.common.cache;

import com.flashsale.common.messaging.MessageEnvelope;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Handles only cache invalidation events; Redis errors must reach the MQ retry path. */
public final class CacheInvalidationMessageConsumer {
    private final StringRedisTemplate redis;

    public CacheInvalidationMessageConsumer(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis);
    }

    public void consume(MessageEnvelope event) {
        if (event == null || !List.of("PRODUCT_CACHE_INVALIDATE", "COUPON_CACHE_INVALIDATE",
                "ACTIVITY_CACHE_INVALIDATE").contains(event.eventType())) {
            throw new IllegalArgumentException("INVALID_CACHE_INVALIDATION_EVENT");
        }
        Map<String, Object> payload = event.payload();
        String type = requireText(payload.get("resource_type"));
        String id = String.valueOf(payload.get("resource_id"));
        String trace = requireText(payload.get("trace_id"));
        if (!type.equals(event.aggregateType()) || !id.equals(event.aggregateId())
                || !trace.equals(event.traceId())) throw new IllegalArgumentException("INVALID_CACHE_INVALIDATION_EVENT");
        if (!(payload.get("cache_keys") instanceof List<?> values) || values.isEmpty()
                || values.stream().anyMatch(value -> !(value instanceof String key) || key.isBlank())) {
            throw new IllegalArgumentException("INVALID_CACHE_INVALIDATION_EVENT");
        }
        List<String> keys = values.stream().map(String.class::cast).toList();
        redis.delete(keys);
    }

    private static String requireText(Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("INVALID_CACHE_INVALIDATION_EVENT");
        return text;
    }
}
