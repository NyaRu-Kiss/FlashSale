package com.flashsale.common.cache;

import com.flashsale.common.messaging.MessageEnvelope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheInvalidationMessageTest {
    @Test void validatesContractAndDeletesRepeatedDeliveryAcrossProducerBoundary() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var consumer = new CacheInvalidationMessageConsumer(redis);
        var event = new MessageEnvelope(UUID.randomUUID(), "PRODUCT_CACHE_INVALIDATE", 1, "PRODUCT_CACHE_INVALIDATE:1",
                "product", "PRODUCT", "1", Instant.now(), "trace-1",
                Map.of("resource_type", "PRODUCT", "resource_id", 1,
                        "cache_keys", List.of("cache:product:public:1", "cache:product:public:list"),
                        "trace_id", "trace-1"));
        consumer.consume(event);
        consumer.consume(event);
        verify(redis, times(2)).delete(List.of("cache:product:public:1", "cache:product:public:list"));
    }

    @Test void redisFailureEscapesSoRocketMqCanRetry() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var consumer = new CacheInvalidationMessageConsumer(redis);
        var event = new MessageEnvelope(UUID.randomUUID(), "COUPON_CACHE_INVALIDATE", 1, "COUPON_CACHE_INVALIDATE:1",
                "coupon", "COUPON_TEMPLATE", "1", Instant.now(), "trace-2",
                Map.of("resource_type", "COUPON_TEMPLATE", "resource_id", 1,
                        "cache_keys", List.of("cache:coupon-template:claimable:list"), "trace_id", "trace-2"));
        doThrow(new IllegalStateException("redis down")).when(redis).delete(List.of("cache:coupon-template:claimable:list"));
        assertThrows(IllegalStateException.class, () -> consumer.consume(event));
    }
}
