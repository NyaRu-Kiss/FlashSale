package com.flashsale.activity;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

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
        when(redis.execute(any(), anyList(), anyString())).thenReturn(1L, 0L);
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

    @Test void acceptedReserveRegistersAnInFlightRequestForPauseBarrier() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString())).thenReturn(18L);
        RedisActivityInventory inventory = new RedisActivityInventory(redis);
        Activity activity = new Activity(9, "sale", 3, 100, 20, 20, 2,
                OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now().plusHours(1),
                ActivityStatus.ACTIVE, false, 7);

        org.junit.jupiter.api.Assertions.assertTrue(inventory.reserve(activity, 8, 2, "request-1").accepted());

        verify(redis).execute(any(), eq(java.util.List.of("activity:9:gate", "activity:9:status",
                "activity:9:stock", "activity:9:reservation:request-1", "activity:9:quota:8",
                "activity:9:reserve:in-flight")), anyString(), anyString(), anyString(), anyString());
    }

    @Test void pauseGateClosesAtomicallyBeforeBarrierCapture() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.execute(any(), anyList(), anyString())).thenReturn(0L);
        when(redis.opsForValue()).thenReturn(values);
        RedisActivityInventory inventory = new RedisActivityInventory(redis);

        inventory.closeGateAndAwaitInFlight(9);

        verify(redis).execute(any(), eq(java.util.List.of("activity:9:gate", "activity:9:reserve:in-flight")), anyString());
    }

    @Test void recoveryKeepsAnExistingStockProjectionUntouched() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString(), anyString())).thenReturn(0L);
        RedisActivityInventory inventory = new RedisActivityInventory(redis);

        inventory.ensureRecoveryProjection(activity(ActivityStatus.ACTIVE), 7);

        DefaultRedisScript<?> script = recoveryScript(redis);
        org.junit.jupiter.api.Assertions.assertTrue(script.getScriptAsString().contains("if redis.call('EXISTS', KEYS[2]) == 1 then"));
        org.junit.jupiter.api.Assertions.assertTrue(script.getScriptAsString().contains("SET', KEYS[1], ARGV[1], 'EX', ARGV[3], 'NX'"));
        org.junit.jupiter.api.Assertions.assertFalse(script.getScriptAsString().contains("SET', KEYS[2], ARGV[2], 'EX', ARGV[3], 'NX'"));
        verify(redis, never()).opsForValue();
    }

    @Test void recoveryAtomicallyBuildsMissingStockAsPausedWithClosedGate() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString(), anyString())).thenReturn(1L);
        RedisActivityInventory inventory = new RedisActivityInventory(redis);

        inventory.ensureRecoveryProjection(activity(ActivityStatus.ACTIVE), 7);

        DefaultRedisScript<?> script = recoveryScript(redis);
        String lua = script.getScriptAsString();
        org.junit.jupiter.api.Assertions.assertTrue(lua.contains("redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])"));
        org.junit.jupiter.api.Assertions.assertTrue(lua.contains("redis.call('SET', KEYS[3], 'PAUSED', 'EX', ARGV[3])"));
        org.junit.jupiter.api.Assertions.assertTrue(lua.contains("redis.call('SET', KEYS[4], 'CLOSED', 'EX', ARGV[3])"));
        verify(redis).execute(any(), eq(java.util.List.of("activity:9:detail", "activity:9:stock",
                "activity:9:status", "activity:9:gate")), anyString(), eq("7"), anyString());
    }

    @SuppressWarnings("unchecked")
    private static DefaultRedisScript<?> recoveryScript(StringRedisTemplate redis) {
        org.mockito.ArgumentCaptor<DefaultRedisScript<Long>> script = org.mockito.ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redis).execute(script.capture(), anyList(), anyString(), anyString(), anyString());
        return script.getValue();
    }

    private static Activity activity(ActivityStatus status) {
        return new Activity(9, "sale", 3, 100, 20, 20, 2,
                OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().plusHours(1), status, false, 7);
    }
}
