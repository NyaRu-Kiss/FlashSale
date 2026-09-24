package com.flashsale.coupon;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RedisCouponClaimInventoryIntegrationTest {
    @Test void concurrentClaimsCannotOversellAndRespectPerUserLimit() throws Exception {
        String host = System.getenv("FLASHSALE_TEST_REDIS_HOST");
        assumeTrue(host != null && !host.isBlank());
        int port = Integer.parseInt(System.getenv().getOrDefault("FLASHSALE_TEST_REDIS_PORT", "6379"));
        var factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        try {
            var redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            long template = Math.abs(UUID.randomUUID().getMostSignificantBits());
            var inventory = new RedisCouponClaimInventory(redis);
            var start = new CountDownLatch(1);
            var accepted = new AtomicInteger();
            try (var pool = Executors.newFixedThreadPool(12)) {
                for (int i = 0; i < 12; i++) {
                    int user = i;
                    pool.submit(() -> {
                        start.await();
                        if (inventory.reserve(template, user, "key-" + user, 5, 0, 2, 0).accepted()) accepted.incrementAndGet();
                        return null;
                    });
                }
                start.countDown();
                pool.shutdown();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }
            assertEquals(5, accepted.get());
            assertEquals("0", redis.opsForValue().get("coupon:" + template + ":stock"));

            long user = 99;
            assertTrue(inventory.reserve(template + 1, user, "first", 3, 0, 1, 0).accepted());
            assertFalse(inventory.reserve(template + 1, user, "second", 3, 0, 1, 0).accepted());
            assertTrue(inventory.compensate(template + 1, user, "first"));
            assertTrue(inventory.reserve(template + 1, user, "retry", 3, 0, 1, 0).accepted());
        } finally {
            factory.destroy();
        }
    }
}
