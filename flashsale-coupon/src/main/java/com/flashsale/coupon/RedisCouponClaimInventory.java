package com.flashsale.coupon;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis admission gate for coupon claims; PostgreSQL remains the final ledger. */
@Component
final class RedisCouponClaimInventory {
    private static final Duration TTL = Duration.ofDays(2);
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[3]) == 1 then return -3 end
            if redis.call('EXISTS', KEYS[1]) == 0 then redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[4]) end
            if redis.call('EXISTS', KEYS[2]) == 0 then redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[4]) end
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            local claimed = tonumber(redis.call('GET', KEYS[2]) or '-1')
            if stock <= 0 then return -1 end
            if claimed >= tonumber(ARGV[3]) then return -2 end
            redis.call('DECR', KEYS[1])
            redis.call('INCR', KEYS[2])
            redis.call('SET', KEYS[3], '1', 'EX', ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> COMPENSATE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[3]) ~= '1' then return 0 end
            redis.call('DEL', KEYS[3])
            redis.call('INCR', KEYS[1])
            redis.call('DECR', KEYS[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    RedisCouponClaimInventory(StringRedisTemplate redis) { this.redis = redis; }

    Reservation reserve(long templateId, long userId, String idempotencyKey,
                        int issueLimit, int issuedCount, int claimLimit, int claimedCount) {
        if (issueLimit <= issuedCount || claimLimit <= claimedCount) {
            return new Reservation(false, issueLimit <= issuedCount ? "COUPON_NOT_AVAILABLE" : "COUPON_CLAIM_LIMIT_EXCEEDED");
        }
        Long result = redis.execute(RESERVE, keys(templateId, userId, idempotencyKey),
                Integer.toString(issueLimit - issuedCount), Integer.toString(claimedCount),
                Integer.toString(claimLimit), Long.toString(TTL.toSeconds()));
        if (result == null || result == -1) return new Reservation(false, "COUPON_NOT_AVAILABLE");
        if (result == -2) return new Reservation(false, "COUPON_CLAIM_LIMIT_EXCEEDED");
        if (result == -3) return new Reservation(false, "REQUEST_IN_PROGRESS");
        return new Reservation(true, "OK");
    }

    boolean compensate(long templateId, long userId, String idempotencyKey) {
        Long result = redis.execute(COMPENSATE, keys(templateId, userId, idempotencyKey));
        return result != null && result == 1;
    }

    private static List<String> keys(long templateId, long userId, String idempotencyKey) {
        String marker = Integer.toHexString(idempotencyKey.hashCode());
        String prefix = "coupon:" + templateId;
        return List.of(prefix + ":stock", prefix + ":quota:" + userId, prefix + ":claim:" + userId + ":" + marker);
    }

    record Reservation(boolean accepted, String code) { }
}
