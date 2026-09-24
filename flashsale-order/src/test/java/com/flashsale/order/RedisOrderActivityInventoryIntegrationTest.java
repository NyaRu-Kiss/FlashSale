package com.flashsale.order;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RedisOrderActivityInventoryIntegrationTest {
    @Test void admissionTracksStockQuotaAndPauseBarrier() {
        String host = System.getenv("FLASHSALE_TEST_REDIS_HOST");
        assumeTrue(host != null && !host.isBlank());
        int port = Integer.parseInt(System.getenv().getOrDefault("FLASHSALE_TEST_REDIS_PORT", "6379"));
        var factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        try {
            var redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            long id = Math.abs(UUID.randomUUID().getMostSignificantBits());
            String prefix = "activity:" + id;
            redis.opsForValue().set(prefix + ":gate", "RESERVE");
            redis.opsForValue().set(prefix + ":status", "ACTIVE");
            redis.opsForValue().set(prefix + ":stock", "2");
            var inventory = new RedisOrderActivityInventory(redis);
            inventory.reserve(id, 7, 1, 1, "first");
            assertEquals("1", redis.opsForValue().get(prefix + ":stock"));
            assertEquals(1, redis.opsForZSet().size(prefix + ":reserve:in-flight"));
            assertThrows(IllegalArgumentException.class, () -> inventory.reserve(id, 7, 1, 1, "second"));
            inventory.complete(id, "first");
            assertEquals(0, redis.opsForZSet().size(prefix + ":reserve:in-flight"));
            inventory.compensate(id, 7, 1, "first");
            inventory.compensate(id, 7, 1, "first");
            assertEquals("2", redis.opsForValue().get(prefix + ":stock"));
            redis.opsForValue().set(prefix + ":gate", "CLOSED");
            assertThrows(IllegalArgumentException.class, () -> inventory.reserve(id, 7, 1, 1, "third"));
        } finally {
            factory.destroy();
        }
    }
}
