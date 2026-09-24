package com.flashsale.activity;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.OffsetDateTime;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisActivityInventoryR05Test {
    @Test void preheatWritesDetailAndNeverOverwritesExistingKeys() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
        RedisActivityInventory inventory = new RedisActivityInventory(redis);
        Activity activity = new Activity(9, "sale", 3, 100, 20, 20, 2,
                OffsetDateTime.now().plusMinutes(5), OffsetDateTime.now().plusHours(1),
                ActivityStatus.NOT_STARTED, false, 7);

        inventory.preheat(activity);

        verify(values).setIfAbsent(eq("activity:9:detail"), contains("\"id\":9"), any(Duration.class));
        verify(values).setIfAbsent(eq("activity:9:stock"), eq("20"), any(Duration.class));
        verify(values).setIfAbsent(eq("activity:9:status"), eq("NOT_STARTED"), any(Duration.class));
        verify(values).setIfAbsent(eq("activity:9:gate"), eq("CLOSED"), any(Duration.class));
        verify(redis).expire(eq("activity:9:detail"), any(Duration.class));
    }

    @Test void activateUsesPreheatKeysAndNeverWritesStockDirectly() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString())).thenReturn(1L);
        RedisActivityInventory inventory = new RedisActivityInventory(redis);
        Activity activity = new Activity(9, "sale", 3, 100, 20, 20, 2,
                OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now().plusHours(1),
                ActivityStatus.NOT_STARTED, true, 7);

        org.junit.jupiter.api.Assertions.assertTrue(inventory.activate(activity));
        verify(redis).execute(any(), eq(java.util.List.of("activity:9:detail", "activity:9:stock",
                "activity:9:status", "activity:9:gate")), anyString());
        verify(redis, never()).opsForValue();
    }

    @Test void preheatedCheckRequiresAllPreheatKeysWithoutWritingRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList())).thenReturn(0L);
        RedisActivityInventory inventory = new RedisActivityInventory(redis);

        org.junit.jupiter.api.Assertions.assertFalse(inventory.hasPreheatedKeys(9));
        verify(redis).execute(any(), eq(java.util.List.of("activity:9:detail", "activity:9:stock",
                "activity:9:status", "activity:9:gate")));
        verify(redis, never()).opsForValue();
    }
}
