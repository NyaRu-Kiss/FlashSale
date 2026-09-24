package com.flashsale.order;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Shares the activity Redis key protocol and keeps the pause in-flight barrier accurate. */
public final class RedisOrderActivityInventory implements OrderActivityInventory {
    private static final Duration TTL = Duration.ofDays(2);
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= 'RESERVE' or redis.call('GET', KEYS[2]) ~= 'ACTIVE' then return -1 end
            if redis.call('EXISTS', KEYS[4]) == 1 then return -4 end
            local stock = tonumber(redis.call('GET', KEYS[3]) or '-1')
            local used = tonumber(redis.call('GET', KEYS[5]) or '0')
            if stock < tonumber(ARGV[1]) then return -2 end
            if used + tonumber(ARGV[1]) > tonumber(ARGV[2]) then return -3 end
            redis.call('DECRBY', KEYS[3], ARGV[1])
            redis.call('INCRBY', KEYS[5], ARGV[1])
            redis.call('SET', KEYS[4], '1', 'EX', ARGV[3])
            local now = redis.call('TIME')[1]
            redis.call('ZADD', KEYS[6], now + 30, ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('SETNX', KEYS[3], '1') == 0 then return 0 end
            redis.call('EXPIRE', KEYS[3], ARGV[2])
            redis.call('INCRBY', KEYS[1], ARGV[1])
            redis.call('DECRBY', KEYS[2], ARGV[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> COMPLETE = new DefaultRedisScript<>("""
            return redis.call('ZREM', KEYS[1], ARGV[1])
            """, Long.class);
    private final StringRedisTemplate redis;
    public RedisOrderActivityInventory(StringRedisTemplate redis) { this.redis = redis; }

    @Override public void reserve(long activityId, long userId, int quantity, int limit, String key) {
        Long result = redis.execute(RESERVE, List.of(gate(activityId), status(activityId), stock(activityId),
                reservation(activityId, key), quota(activityId, userId), inFlight(activityId)),
                Integer.toString(quantity), Integer.toString(limit), Long.toString(TTL.toSeconds()), key);
        if (result == null || result == -1) throw new IllegalArgumentException("ACTIVITY_NOT_ACTIVE");
        if (result == -2) throw new IllegalArgumentException("STOCK_NOT_ENOUGH");
        if (result == -3) throw new IllegalArgumentException("ACTIVITY_PURCHASE_LIMIT_EXCEEDED");
        if (result == -4) throw new IllegalArgumentException("IDEMPOTENCY_PROCESSING");
    }

    @Override public void complete(long activityId, String key) {
        redis.execute(COMPLETE, List.of(inFlight(activityId)), key);
    }

    @Override public void compensate(long activityId, long userId, int quantity, String key) {
        Long changed = redis.execute(RELEASE, List.of(stock(activityId), quota(activityId, userId),
                release(activityId, key)), Integer.toString(quantity), Long.toString(TTL.toSeconds()));
        if (changed == null) throw new IllegalStateException("ACTIVITY_RESERVATION_COMPENSATION_FAILED");
        complete(activityId, key);
    }

    private static String stock(long id) { return "activity:" + id + ":stock"; }
    private static String status(long id) { return "activity:" + id + ":status"; }
    private static String gate(long id) { return "activity:" + id + ":gate"; }
    private static String quota(long id, long user) { return "activity:" + id + ":quota:" + user; }
    private static String reservation(long id, String key) { return "activity:" + id + ":reservation:" + key; }
    private static String release(long id, String key) { return "activity:" + id + ":release:" + key; }
    private static String inFlight(long id) { return "activity:" + id + ":reserve:in-flight"; }
}
