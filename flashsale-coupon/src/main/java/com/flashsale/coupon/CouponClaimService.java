package com.flashsale.coupon;

import com.flashsale.common.security.Principal;
import com.flashsale.common.trace.TraceContext;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class CouponClaimService {
    private final JdbcTemplate jdbc;
    private final RedisCouponClaimInventory redis;
    private final TransactionTemplate transactions;

    CouponClaimService(JdbcTemplate jdbc, RedisCouponClaimInventory redis) { this(jdbc, redis, null); }

    @org.springframework.beans.factory.annotation.Autowired
    CouponClaimService(JdbcTemplate jdbc, RedisCouponClaimInventory redis, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.transactions = manager == null ? null : new TransactionTemplate(manager);
    }

    long claim(Principal principal, long templateId, String idempotencyKey) {
        if (principal == null || principal.role() != com.flashsale.common.security.Role.CUSTOMER) {
            throw new IllegalArgumentException("FORBIDDEN");
        }
        String key = normalizeKey(idempotencyKey);
        String fingerprint = CouponClaimRequestFingerprint.sha256(principal.userId(), templateId);
        ClaimDecision decision = inTransaction(() -> claimIdempotency(principal.userId(), templateId, key, fingerprint));
        if (decision.replayed()) return decision.userCouponId();

        boolean[] reserved = {false};
        try {
            return inTransaction(() -> createClaim(principal, templateId, key, fingerprint, reserved));
        } catch (RuntimeException failure) {
            if (reserved[0] && !redis.compensate(templateId, principal.userId(), fingerprint)) {
                recordFailure(principal.userId(), templateId, key, fingerprint, "COUPON_REDIS_COMPENSATION_FAILED");
                throw new IllegalStateException("COUPON_REDIS_COMPENSATION_FAILED", failure);
            }
            String code = failure instanceof ClaimFailure claimFailure ? claimFailure.code : "COUPON_CLAIM_FAILED";
            recordFailure(principal.userId(), templateId, key, fingerprint, code);
            throw failure instanceof ClaimFailure ? new IllegalArgumentException(code) : failure;
        }
    }

    private Long createClaim(Principal principal, long templateId, String key, String fingerprint, boolean[] reserved) {
        ClaimConfig config = jdbc.query("""
                select issue_limit,issued_count,claim_limit_per_user,status::text,
                       now() between claim_starts_at and claim_ends_at
                from coupon_template where id=?
                """, (rs, row) -> new ClaimConfig(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getBoolean(5)), templateId)
                .stream().findFirst().orElseThrow(() -> new ClaimFailure("COUPON_NOT_AVAILABLE"));
        if (!config.claimable()) throw new ClaimFailure("COUPON_NOT_AVAILABLE");
        Integer claimed = jdbc.query("select claimed_count from coupon_user_claim_counter where coupon_template_id=? and user_id=?",
                (rs, row) -> rs.getInt(1), templateId, principal.userId()).stream().findFirst().orElse(0);
        RedisCouponClaimInventory.Reservation reservation = redis.reserve(templateId, principal.userId(), fingerprint,
                config.issueLimit(), config.issuedCount(), config.claimLimit(), claimed);
        if (!reservation.accepted()) throw new ClaimFailure(reservation.code());
        reserved[0] = true;
        if (jdbc.update("update coupon_template set issued_count=issued_count+1,version=version+1 where id=? and issued_count<issue_limit", templateId) != 1) {
            throw new ClaimFailure("COUPON_NOT_AVAILABLE");
        }
        if (jdbc.update("""
                insert into coupon_user_claim_counter(coupon_template_id,user_id,claimed_count) values(?,?,1)
                on conflict(coupon_template_id,user_id) do update set claimed_count=coupon_user_claim_counter.claimed_count+1
                where coupon_user_claim_counter.claimed_count<?
                """, templateId, principal.userId(), config.claimLimit()) != 1) {
            throw new ClaimFailure("COUPON_CLAIM_LIMIT_EXCEEDED");
        }
        Long coupon = jdbc.queryForObject("insert into user_coupon(coupon_template_id,user_id) values(?,?) returning id", Long.class, templateId, principal.userId());
        jdbc.update("""
                update coupon_claim_idempotency set status='SUCCEEDED',user_coupon_id=?,response_code='SUCCESS',completed_at=now()
                where user_id=? and coupon_template_id=? and idempotency_key=? and status='PROCESSING'
                """, coupon, principal.userId(), templateId, key);
        jdbc.update("""
                insert into coupon_outbox(event_id,event_type,idempotency_key,aggregate_type,aggregate_id,payload,trace_id)
                values(?,?,?,?,?,?,?)
                """, UUID.randomUUID(), "COUPON_CLAIMED", "COUPON_CLAIM_" + key, "USER_COUPON", coupon,
                "{\"template_id\":" + templateId + ",\"user_id\":" + principal.userId() + "}", TraceContext.getOrCreate());
        return coupon;
    }

    private ClaimDecision claimIdempotency(long userId, long templateId, String key, String fingerprint) {
        int inserted = jdbc.update("""
                insert into coupon_claim_idempotency(user_id,coupon_template_id,idempotency_key,request_fingerprint)
                values(?,?,?,?) on conflict (user_id,coupon_template_id,idempotency_key) do nothing
                """, userId, templateId, key, fingerprint);
        if (inserted == 1) return new ClaimDecision(false, null);
        ClaimRow row = jdbc.queryForObject("""
                select request_fingerprint,status::text,user_coupon_id,response_code,created_at
                from coupon_claim_idempotency where user_id=? and coupon_template_id=? and idempotency_key=?
                """, (rs, n) -> new ClaimRow(rs.getString(1), rs.getString(2), rs.getObject(3, Long.class), rs.getString(4), rs.getObject(5, OffsetDateTime.class)),
                userId, templateId, key);
        if (!fingerprint.equals(row.fingerprint())) throw new ClaimFailure("IDEMPOTENCY_CONFLICT");
        if ("SUCCEEDED".equals(row.status())) return new ClaimDecision(true, row.userCouponId());
        boolean expired = "PROCESSING".equals(row.status()) && row.createdAt().isBefore(OffsetDateTime.now().minusMinutes(5));
        if ("PROCESSING".equals(row.status()) && !expired) throw new ClaimFailure("REQUEST_IN_PROGRESS");
        int reclaimed = jdbc.update("""
                update coupon_claim_idempotency set status='PROCESSING',user_coupon_id=null,response_code=null,created_at=now(),completed_at=null
                where user_id=? and coupon_template_id=? and idempotency_key=? and request_fingerprint=?
                  and (status in ('FAILED','REJECTED') or (status='PROCESSING' and created_at < now() - interval '5 minutes'))
                """, userId, templateId, key, fingerprint);
        if (reclaimed != 1) throw new ClaimFailure("REQUEST_IN_PROGRESS");
        return new ClaimDecision(false, null);
    }

    private void recordFailure(long userId, long templateId, String key, String fingerprint, String code) {
        try {
            inTransaction(() -> {
                jdbc.update("""
                        update coupon_claim_idempotency set status='FAILED',response_code=?,completed_at=now()
                        where user_id=? and coupon_template_id=? and idempotency_key=? and request_fingerprint=? and status='PROCESSING'
                        """, code, userId, templateId, key, fingerprint);
                return null;
            });
        } catch (RuntimeException ignored) { }
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) throw new IllegalArgumentException("VALIDATION_ERROR");
        return key.trim();
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactions == null ? action.get() : transactions.execute(status -> action.get());
    }

    java.util.List<UserCouponView> mine(Principal p, String status) {
        if (p == null || p.role() != com.flashsale.common.security.Role.CUSTOMER) throw new IllegalArgumentException("FORBIDDEN");
        if (status != null && !status.isBlank() && !Set.of("AVAILABLE", "RESERVED", "CONSUMED", "EXPIRED").contains(status)) throw new IllegalArgumentException("VALIDATION_ERROR");
        return jdbc.query("""
                select uc.id,uc.coupon_template_id,uc.status,uc.claimed_at,ct.threshold_minor,ct.discount_minor,ct.use_starts_at,ct.use_ends_at
                from user_coupon uc join coupon_template ct on ct.id=uc.coupon_template_id
                where uc.user_id=? and (? is null or uc.status=?) order by uc.id desc
                """, (r, n) -> new UserCouponView(r.getLong(1), r.getLong(2), r.getString(3), r.getObject(4, OffsetDateTime.class), r.getLong(5), r.getLong(6), r.getObject(7, OffsetDateTime.class), r.getObject(8, OffsetDateTime.class)),
                p.userId(), status == null || status.isBlank() ? null : status, status == null || status.isBlank() ? null : status);
    }

    private record ClaimConfig(int issueLimit, int issuedCount, int claimLimit, String status, boolean inWindow) { boolean claimable() { return "ACTIVE".equals(status) && inWindow; } }
    private record ClaimRow(String fingerprint, String status, Long userCouponId, String responseCode, OffsetDateTime createdAt) { }
    private record ClaimDecision(boolean replayed, Long userCouponId) { }
    private static final class ClaimFailure extends RuntimeException { private final String code; private ClaimFailure(String code) { this.code = code; } }
    record UserCouponView(long id, long templateId, String status, OffsetDateTime claimedAt, long thresholdMinor, long discountMinor, OffsetDateTime useStartsAt, OffsetDateTime useEndsAt) { }
}
