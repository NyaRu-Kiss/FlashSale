package com.flashsale.activity;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis Lua projection. Existing keys are never overwritten during normal preheat. */
@Component
final class RedisActivityInventory implements ActivityInventoryPort {
    private static final Duration TTL = Duration.ofDays(2);
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local gate = redis.call('GET', KEYS[1])
            local status = redis.call('GET', KEYS[2])
            if gate ~= 'RESERVE' or status ~= 'ACTIVE' then return -1 end
            if redis.call('EXISTS', KEYS[4]) == 1 then return -4 end
            local stock = tonumber(redis.call('GET', KEYS[3]) or '-1')
            local used = tonumber(redis.call('GET', KEYS[5]) or '0')
            if stock < tonumber(ARGV[1]) then return -2 end
            if used + tonumber(ARGV[1]) > tonumber(ARGV[2]) then return -3 end
            redis.call('DECRBY', KEYS[3], ARGV[1])
            redis.call('INCRBY', KEYS[5], ARGV[1])
            redis.call('SET', KEYS[4], '1', 'EX', ARGV[3])
            return stock - tonumber(ARGV[1])
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('SETNX', KEYS[4], '1') == 0 then return 0 end
            redis.call('EXPIRE', KEYS[4], ARGV[2])
            redis.call('INCRBY', KEYS[1], ARGV[1])
            redis.call('DECRBY', KEYS[2], ARGV[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ACTIVATE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 or redis.call('EXISTS', KEYS[2]) == 0 then return 0 end
            if redis.call('GET', KEYS[3]) ~= 'NOT_STARTED' then return 0 end
            redis.call('SET', KEYS[3], 'ACTIVE', 'EX', ARGV[1])
            redis.call('SET', KEYS[4], 'RESERVE', 'EX', ARGV[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> PREHEATED = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 or redis.call('EXISTS', KEYS[2]) == 0
                    or redis.call('EXISTS', KEYS[3]) == 0 or redis.call('EXISTS', KEYS[4]) == 0 then return 0 end
            if redis.call('GET', KEYS[3]) ~= 'NOT_STARTED' then return 0 end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    RedisActivityInventory(StringRedisTemplate redis) { this.redis = redis; }

    @Override public void preheat(Activity activity) {
        setIfAbsent(detailKey(activity.id()), detail(activity));
        setIfAbsent(stockKey(activity.id()), Integer.toString(activity.initialStock()));
        setIfAbsent(statusKey(activity.id()), activity.status().name());
        setIfAbsent(gateKey(activity.id()), activity.status() == ActivityStatus.ACTIVE ? "RESERVE" : "CLOSED");
        redis.expire(detailKey(activity.id()), TTL); redis.expire(stockKey(activity.id()), TTL); redis.expire(statusKey(activity.id()), TTL); redis.expire(gateKey(activity.id()), TTL);
    }

    @Override public Reservation reserve(Activity activity, long userId, int quantity, String reservationKey) {
        if (quantity <= 0) throw new IllegalArgumentException("VALIDATION_ERROR");
        Long result = redis.execute(RESERVE,
                java.util.List.of(gateKey(activity.id()), statusKey(activity.id()), stockKey(activity.id()),
                        reservationKey(activity.id(), reservationKey), quotaKey(activity.id(), userId)),
                Integer.toString(quantity), Integer.toString(activity.purchaseLimitPerUser()),
                Long.toString(TTL.toSeconds()));
        if (result == null || result == -1) return new Reservation(false, -1, "ACTIVITY_NOT_ACTIVE");
        if (result == -2) return new Reservation(false, 0, "STOCK_NOT_ENOUGH");
        if (result == -3) return new Reservation(false, 0, "ACTIVITY_PURCHASE_LIMIT_EXCEEDED");
        if (result == -4) return new Reservation(true, -1, "DUPLICATE");
        return new Reservation(true, result.intValue(), "OK");
    }

    @Override public boolean release(Activity activity, long userId, int quantity, String reservationKey) {
        Long result = redis.execute(RELEASE,
                java.util.List.of(stockKey(activity.id()), quotaKey(activity.id(), userId),
                        releaseKey(activity.id(), reservationKey)),
                Integer.toString(quantity), Long.toString(TTL.toSeconds()));
        return result != null && result == 1;
    }

    @Override public void closeGate(long activityId) { redis.opsForValue().set(gateKey(activityId), "CLOSED", TTL); }

    @Override public boolean hasPreheatedKeys(long activityId) {
        Long result = redis.execute(PREHEATED,
                java.util.List.of(detailKey(activityId), stockKey(activityId), statusKey(activityId), gateKey(activityId)));
        return result != null && result == 1;
    }

    @Override public boolean activate(Activity activity) {
        Long result = redis.execute(ACTIVATE,
                java.util.List.of(detailKey(activity.id()), stockKey(activity.id()), statusKey(activity.id()), gateKey(activity.id())),
                Long.toString(TTL.toSeconds()));
        return result != null && result == 1;
    }

    @Override public void rebuild(Activity activity, int availableStock) {
        redis.opsForValue().set(stockKey(activity.id()), Integer.toString(availableStock), TTL);
        redis.opsForValue().set(statusKey(activity.id()), activity.status().name(), TTL);
        redis.opsForValue().set(gateKey(activity.id()), activity.status() == ActivityStatus.ACTIVE ? "RESERVE" : "CLOSED", TTL);
    }

    private void setIfAbsent(String key, String value) { redis.opsForValue().setIfAbsent(key, value, TTL); }
    private String detail(Activity a) {
        return "{\"id\":" + a.id() + ",\"name\":\"" + escape(a.name()) + "\",\"productId\":" + a.productId()
                + ",\"salePriceMinor\":" + a.salePriceMinor() + ",\"initialStock\":" + a.initialStock()
                + ",\"availableStock\":" + a.availableStock() + ",\"purchaseLimitPerUser\":" + a.purchaseLimitPerUser()
                + ",\"startsAt\":\"" + a.startsAt() + "\",\"endsAt\":\"" + a.endsAt()
                + "\",\"status\":\"" + a.status() + "\"}";
    }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    static String detailKey(long id) { return "activity:" + id + ":detail"; }
    static String stockKey(long id) { return "activity:" + id + ":stock"; }
    static String statusKey(long id) { return "activity:" + id + ":status"; }
    static String gateKey(long id) { return "activity:" + id + ":gate"; }
    private static String quotaKey(long id, long userId) { return "activity:" + id + ":quota:" + userId; }
    private static String reservationKey(long id, String key) { return "activity:" + id + ":reservation:" + key; }
    private static String releaseKey(long id, String key) { return "activity:" + id + ":release:" + key; }
}
