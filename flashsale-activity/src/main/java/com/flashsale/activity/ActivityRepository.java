package com.flashsale.activity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.trace.TraceContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL authority for activity state. Every lifecycle write is conditional. */
@Repository
final class ActivityRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    ActivityRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    Activity create(Activity input, long actor) {
        Activity result = jdbc.queryForObject("""
                insert into marketing_activity
                  (name, product_id, sale_price_minor, initial_stock, available_stock,
                   purchase_limit_per_user, starts_at, ends_at, status, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'NOT_STARTED', ?, ?)
                returning id, name, product_id, sale_price_minor, initial_stock, available_stock,
                          purchase_limit_per_user, starts_at, ends_at, status, updated_by
                """, (rs, n) -> map(rs), input.name(), input.productId(), input.salePriceMinor(),
                input.initialStock(), input.initialStock(), input.purchaseLimitPerUser(),
                input.startsAt(), input.endsAt(), actor, actor);
        jdbc.update("insert into activity_inventory_sequence(activity_id) values (?)", result.id());
        jdbc.update("insert into activity_inventory_checkpoint(activity_id) values (?)", result.id());
        String traceId = TraceContext.getOrCreate();
        String payload = json(Map.of("resource_type", "ACTIVITY", "resource_id", result.id(),
                "cache_keys", List.of(RedisActivityInventory.detailKey(result.id())), "trace_id", traceId));
        jdbc.update("""
                insert into activity_outbox(event_id, event_type, idempotency_key, aggregate_type,
                    aggregate_id, payload, trace_id)
                values (?, 'ACTIVITY_CACHE_INVALIDATE', ?, 'ACTIVITY', ?, ?::jsonb, ?)
                """, UUID.randomUUID(), "ACTIVITY:CREATE:" + result.id(),
                Long.toString(result.id()), payload, traceId);
        audit(actor, result.id(), "CREATE", null, result);
        return result;
    }

    void audit(long operatorId, long targetId, String action, Activity before, Activity after) {
        jdbc.update("""
                insert into operator_audit_log(operator_id, target_type, target_id, action,
                    before_snapshot, after_snapshot, trace_id, request_source)
                values (?, 'ACTIVITY', ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?)
                """, operatorId, targetId, action, snapshot(before), snapshot(after),
                TraceContext.getOrCreate(), "operator:" + operatorId);
    }

    Activity find(long id) {
        return jdbc.query("""
                select id, name, product_id, sale_price_minor, initial_stock, available_stock,
                       purchase_limit_per_user, starts_at, ends_at, status, updated_by
                  from marketing_activity where id = ?
                """, (rs, n) -> map(rs), id).stream().findFirst().orElse(null);
    }

    Activity findPreheatCandidate(long id, Duration window) {
        return jdbc.query("""
                select id, name, product_id, sale_price_minor, initial_stock, available_stock,
                       purchase_limit_per_user, starts_at, ends_at, status, updated_by
                  from marketing_activity
                 where id = ? and status = 'NOT_STARTED'
                   and starts_at > now()
                   and starts_at <= now() + (? * interval '1 millisecond')
                """, (rs, n) -> map(rs), id, window.toMillis()).stream().findFirst().orElse(null);
    }

    List<Activity> findPreheatCandidates(Duration window) {
        return jdbc.query("""
                select id, name, product_id, sale_price_minor, initial_stock, available_stock,
                       purchase_limit_per_user, starts_at, ends_at, status, updated_by
                  from marketing_activity
                 where status = 'NOT_STARTED'
                   and starts_at > now()
                   and starts_at <= now() + (? * interval '1 millisecond')
                 order by starts_at, id
                """, (rs, n) -> map(rs), window.toMillis());
    }

    void recordPreheatReady(Activity activity, String traceId) {
        String detailKey = RedisActivityInventory.detailKey(activity.id());
        String stockKey = RedisActivityInventory.stockKey(activity.id());
        String statusKey = RedisActivityInventory.statusKey(activity.id());
        String gateKey = RedisActivityInventory.gateKey(activity.id());
        String payload = "{\"activity_id\":" + activity.id()
                + ",\"detail_key\":\"" + detailKey + "\""
                + ",\"stock_key\":\"" + stockKey + "\""
                + ",\"status_key\":\"" + statusKey + "\""
                + ",\"gate_key\":\"" + gateKey + "\""
                + ",\"trace_id\":\"" + traceId + "\""
                + ",\"preheated_at\":\"" + OffsetDateTime.now() + "\"}";
        jdbc.update("""
                insert into activity_outbox(event_id, event_type, idempotency_key, aggregate_type,
                    aggregate_id, payload, trace_id)
                values (?, 'ACTIVITY_PREHEAT_READY', ?, 'ACTIVITY', ?, ?::jsonb, ?)
                on conflict (idempotency_key) do nothing
                """, UUID.randomUUID(), "ACTIVITY:PREHEAT_READY:" + activity.id(),
                Long.toString(activity.id()), payload, traceId);
    }

    Activity findPublic(long id) {
        return jdbc.query("""
                select id, name, product_id, sale_price_minor, initial_stock, available_stock,
                       purchase_limit_per_user, starts_at, ends_at, status, updated_by
                  from marketing_activity
                 where id = ? and status = 'ACTIVE' and starts_at <= now() and now() < ends_at
                """, (rs, n) -> map(rs), id).stream().findFirst().orElse(null);
    }

    List<Activity> listPublic() {
        return jdbc.query("""
                select id, name, product_id, sale_price_minor, initial_stock, available_stock,
                       purchase_limit_per_user, starts_at, ends_at, status, updated_by
                  from marketing_activity
                 where status = 'ACTIVE' and starts_at <= now() and now() < ends_at
                 order by id
                """, (rs, n) -> map(rs));
    }

    List<Activity> listAll() {
        return jdbc.query("""
                select id, name, product_id, sale_price_minor, initial_stock, available_stock,
                       purchase_limit_per_user, starts_at, ends_at, status, updated_by
                  from marketing_activity order by id
                """, (rs, n) -> map(rs));
    }

    long countPublic() { return count("status = 'ACTIVE' and starts_at <= now() and now() < ends_at"); }
    long countAll() { return count("true"); }

    Activity casStatus(long id, ActivityStatus expected, ActivityStatus target, long actor) {
        return jdbc.query("""
                update marketing_activity
                   set status = ?::activity_status, updated_by = ?, version = version + 1
                 where id = ? and status = ?::activity_status
                returning id, name, product_id, sale_price_minor, initial_stock, available_stock,
                          purchase_limit_per_user, starts_at, ends_at, status, updated_by
                """, (rs, n) -> map(rs), target.name(), actor, id, expected.name()).stream().findFirst().orElse(null);
    }

    Activity casStatusAt(long id, ActivityStatus expected, ActivityStatus target, long actor) {
        return jdbc.query("""
                update marketing_activity
                   set status = ?::activity_status, updated_by = ?, version = version + 1
                 where id = ? and status = ?::activity_status
                   and ((?::activity_status <> 'ACTIVE') or (starts_at <= now() and now() < ends_at))
                returning id, name, product_id, sale_price_minor, initial_stock, available_stock,
                          purchase_limit_per_user, starts_at, ends_at, status, updated_by
                """, (rs, n) -> map(rs), target.name(), actor, id, expected.name(), target.name()).stream().findFirst().orElse(null);
    }

    Activity endIfDue(long id, long actor) {
        return jdbc.query("""
                update marketing_activity set status = 'ENDED', updated_by = ?, version = version + 1
                 where id = ? and status = 'ACTIVE' and ends_at <= now()
                returning id, name, product_id, sale_price_minor, initial_stock, available_stock,
                          purchase_limit_per_user, starts_at, ends_at, status, updated_by
                """, (rs, n) -> map(rs), actor, id).stream().findFirst().orElse(null);
    }

    Activity pauseWithBarrier(long id, long barrier, long actor) {
        return jdbc.query("""
                update marketing_activity
                   set status = 'PAUSED', pause_barrier_sequence = ?, updated_by = ?, version = version + 1
                 where id = ? and status = 'ACTIVE'
                returning id, name, product_id, sale_price_minor, initial_stock, available_stock,
                          purchase_limit_per_user, starts_at, ends_at, status, updated_by
                """, (rs, n) -> map(rs), barrier, actor, id).stream().findFirst().orElse(null);
    }

    long lockAndReadBarrier(long activityId) {
        Long barrier = jdbc.queryForObject("select next_event_sequence - 1 from activity_inventory_sequence where activity_id = ? for update", Long.class, activityId);
        if (barrier == null) throw new IllegalArgumentException("ACTIVITY_NOT_FOUND");
        return barrier;
    }

    long lastEventSequence(long activityId) {
        Long value = jdbc.queryForObject("select coalesce(max(event_sequence), 0) from activity_inventory_event where activity_id = ?", Long.class, activityId);
        return value == null ? 0 : value;
    }

    long quotaUsers(long activityId) { Long value = jdbc.queryForObject("select count(*) from activity_user_quota where activity_id = ?", Long.class, activityId); return value == null ? 0 : value; }

    private long count(String predicate) {
        Long value = jdbc.queryForObject("select count(*) from marketing_activity where " + predicate, Long.class);
        return value == null ? 0 : value;
    }

    private Activity map(ResultSet rs) throws SQLException {
        return new Activity(rs.getLong("id"), rs.getString("name"), rs.getLong("product_id"),
                rs.getLong("sale_price_minor"), rs.getInt("initial_stock"), rs.getInt("available_stock"),
                rs.getInt("purchase_limit_per_user"), rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class), ActivityStatus.valueOf(rs.getString("status")),
                false, rs.getLong("updated_by"));
    }

    private String snapshot(Activity value) {
        if (value == null) return null;
        return json(value);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("AUDIT_SERIALIZATION_FAILED", e); }
    }
}
