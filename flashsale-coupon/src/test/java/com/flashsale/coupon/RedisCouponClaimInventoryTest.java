package com.flashsale.coupon;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisCouponClaimInventoryTest {
    @Test void luaAtomicallyReservesStockQuotaAndDuplicateMarker() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString())).thenReturn(1L);
        RedisCouponClaimInventory inventory = new RedisCouponClaimInventory(redis);

        String fingerprint = CouponClaimRequestFingerprint.sha256(7, 9);
        assertTrue(inventory.reserve(9, 7, fingerprint, 10, 2, 2, 0).accepted());

        ArgumentCaptor<DefaultRedisScript<Long>> script = ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redis).execute(script.capture(), eq(List.of("coupon:9:stock", "coupon:9:quota:7", "coupon:9:claim:7:" + fingerprint)),
                eq("8"), eq("0"), eq("2"), anyString());
        String lua = script.getValue().getScriptAsString();
        assertTrue(lua.contains("EXISTS', KEYS[3]"));
        assertTrue(lua.contains("DECR', KEYS[1]"));
        assertTrue(lua.contains("INCR', KEYS[2]"));
        assertTrue(lua.contains("SET', KEYS[3]"));
    }

    @Test void redisResultsMapToExhaustionLimitAndDuplicate() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(-1L, -2L, -3L);
        RedisCouponClaimInventory inventory = new RedisCouponClaimInventory(redis);

        assertEquals("COUPON_NOT_AVAILABLE", inventory.reserve(1, 1, "a", 1, 0, 1, 0).code());
        assertEquals("COUPON_CLAIM_LIMIT_EXCEEDED", inventory.reserve(1, 1, "b", 2, 0, 1, 0).code());
        assertEquals("REQUEST_IN_PROGRESS", inventory.reserve(1, 1, "c", 2, 0, 1, 0).code());
    }

    @Test void compensationLuaRestoresStockAndQuotaOnlyOnce() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList())).thenReturn(1L, 0L);
        RedisCouponClaimInventory inventory = new RedisCouponClaimInventory(redis);

        String fingerprint = CouponClaimRequestFingerprint.sha256(7, 9);
        assertTrue(inventory.compensate(9, 7, fingerprint));
        assertFalse(inventory.compensate(9, 7, fingerprint));
        verify(redis, times(2)).execute(any(), eq(List.of("coupon:9:stock", "coupon:9:quota:7", "coupon:9:claim:7:" + fingerprint)));
    }
}
