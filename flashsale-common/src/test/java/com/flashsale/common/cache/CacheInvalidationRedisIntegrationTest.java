package com.flashsale.common.cache;

import com.flashsale.common.messaging.MessageEnvelope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

class CacheInvalidationRedisIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_REDIS_PORT", matches = "[0-9]+")
    void anotherServiceCanConsumeDuplicateEventAgainstRedis() {
        int port = Integer.parseInt(System.getenv("TEST_REDIS_PORT"));
        var connection = new LettuceConnectionFactory("127.0.0.1", port);
        connection.afterPropertiesSet();
        try {
            var redis = new StringRedisTemplate(connection);
            redis.afterPropertiesSet();
            String key = "cache:product:public:r19-integration";
            redis.opsForValue().set(key, "stale");
            var event = new MessageEnvelope(UUID.randomUUID(), "PRODUCT_CACHE_INVALIDATE", 1,
                    "PRODUCT_CACHE_INVALIDATE:r19", "product", "PRODUCT", "19", Instant.now(), "trace-r19",
                    Map.of("resource_type", "PRODUCT", "resource_id", 19,
                            "cache_keys", List.of(key), "trace_id", "trace-r19"));
            var consumer = new CacheInvalidationMessageConsumer(redis);
            consumer.consume(event);
            consumer.consume(event);
            assertNull(redis.opsForValue().get(key));
        } finally {
            connection.destroy();
        }
    }
}
