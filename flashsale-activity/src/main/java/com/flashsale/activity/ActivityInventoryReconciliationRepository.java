package com.flashsale.activity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL evidence checks required before a paused activity may be recovered. */
@Repository
class ActivityInventoryReconciliationRepository {
    private final JdbcTemplate jdbc;

    ActivityInventoryReconciliationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void assertConsistent(long activityId, long barrier, int initialStock, int availableStock) {
        if (!isTrue("""
                select count(*) = ? and coalesce(min(event_sequence), 1) = 1
                   and coalesce(max(event_sequence), 0) = ?
                  from activity_inventory_event where activity_id = ? and event_sequence <= ?
                """, barrier, barrier, activityId, barrier)) throw new IllegalStateException("ACTIVITY_LEDGER_GAP");
        if (!isTrue("""
                select not exists (
                  select 1 from activity_inventory_event e
                   where e.activity_id = ? and e.event_sequence <= ? and not exists (
                     select 1 from inventory_movement m
                      where m.source = 'ACTIVITY' and m.activity_id = e.activity_id
                        and m.activity_inventory_event_sequence = e.event_sequence
                        and m.quantity_delta = e.quantity_delta))
                """, activityId, barrier)) throw new IllegalStateException("ACTIVITY_MOVEMENT_MISMATCH");
        if (!isTrue("""
                select not exists (
                  select 1 from activity_inventory_event e left join inventory_reservation r on r.id = e.reservation_id
                   where e.activity_id = ? and e.event_sequence <= ?
                     and (r.id is null or r.source <> 'ACTIVITY' or r.activity_id <> e.activity_id
                          or (e.kind = 'RESERVE' and (r.activity_reserve_sequence <> e.event_sequence
                                                       or r.quantity <> -e.quantity_delta))))
                """, activityId, barrier)) throw new IllegalStateException("ACTIVITY_RESERVATION_MISMATCH");
        if (availableStock != initialStock + eventDelta(activityId, barrier))
            throw new IllegalStateException("ACTIVITY_STOCK_PROJECTION_MISMATCH");
        long effectiveReserved = value("""
                select coalesce(sum(quantity), 0) from inventory_reservation
                 where source = 'ACTIVITY' and activity_id = ? and status in ('RESERVED', 'CONFIRMED')
                """, activityId);
        if (effectiveReserved != initialStock - availableStock)
            throw new IllegalStateException("ACTIVITY_EFFECTIVE_RESERVATION_MISMATCH");
    }

    private long eventDelta(long activityId, long barrier) {
        return value("select coalesce(sum(quantity_delta), 0) from activity_inventory_event where activity_id = ? and event_sequence <= ?", activityId, barrier);
    }
    private boolean isTrue(String sql, Object... args) { return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args)); }
    private long value(String sql, Object... args) { Long value = jdbc.queryForObject(sql, Long.class, args); return value == null ? 0 : value; }
}
