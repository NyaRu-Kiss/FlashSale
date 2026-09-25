package com.flashsale.activity;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Allocates contiguous activity event sequences under a row lock. */
@Repository
class ActivityInventoryEventRepository {
    private final JdbcTemplate jdbc;
    ActivityInventoryEventRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @org.springframework.transaction.annotation.Transactional
    ActivityInventoryLedger.Event append(long activityId, ActivityInventoryLedger.Kind kind, int quantity,
                                         Long reservationId, String producer, String traceId) {
        if (quantity <= 0) throw new IllegalArgumentException("VALIDATION_ERROR");
        Long sequence = jdbc.queryForObject("""
                update activity_inventory_sequence
                   set next_event_sequence = next_event_sequence + 1
                 where activity_id = ?
                returning next_event_sequence - 1
                """, Long.class, activityId);
        if (sequence == null) throw new IllegalArgumentException("ACTIVITY_NOT_FOUND");
        UUID eventId = UUID.randomUUID(); UUID outboxId = UUID.randomUUID();
        int delta = kind == ActivityInventoryLedger.Kind.RESERVE ? -quantity : quantity;
        jdbc.update("""
                insert into activity_inventory_event(event_id, activity_id, event_sequence, reservation_id,
                    kind, quantity_delta, producer, outbox_event_id)
                values (?, ?, ?, ?, ?::activity_inventory_event_kind, ?, ?, ?)
                """, eventId, activityId, sequence, reservationId, kind.name(), delta, producer, outboxId);
        jdbc.update("""
                insert into activity_outbox(event_id, event_type, idempotency_key, aggregate_type, aggregate_id,
                    payload, trace_id)
                values (?, 'ACTIVITY_INVENTORY_' || ?, ?, 'ACTIVITY', ?, ?::jsonb, ?)
                """, outboxId, kind.name(), "ACTIVITY:INVENTORY:" + eventId, Long.toString(activityId),
                "{\"event_id\":\"" + eventId + "\",\"idempotency_key\":\"ACTIVITY_INVENTORY:" + eventId
                        + "\",\"activity_id\":" + activityId + ",\"sequence\":" + sequence
                        + ",\"kind\":\"" + kind.name() + "\",\"quantity\":" + quantity
                        + ",\"trace_id\":\"" + traceId + "\"}", traceId);
        return new ActivityInventoryLedger.Event(sequence, kind, quantity);
    }

    long checkpoint(long activityId) { return value("select last_contiguous_sequence from activity_inventory_checkpoint where activity_id = ?", activityId); }
    long lastSequence(long activityId) { return value("select coalesce(max(event_sequence), 0) from activity_inventory_event where activity_id = ?", activityId); }
    void advanceCheckpoint(long activityId, long sequence) {
        int changed = jdbc.update("""
                update activity_inventory_checkpoint set last_contiguous_sequence = ?, updated_at = now()
                 where activity_id = ? and last_contiguous_sequence + 1 = ?
                """, sequence, activityId, sequence);
        if (changed != 1) throw new IllegalArgumentException("CHECKPOINT_GAP");
    }
    List<Long> unsentBefore(long activityId, long barrier) {
        return jdbc.queryForList("""
                select e.event_sequence from activity_inventory_event e
                 join activity_outbox o on o.event_id = e.outbox_event_id
                where e.activity_id = ? and e.event_sequence <= ? and o.status <> 'SENT'
                order by e.event_sequence
                """, Long.class, activityId, barrier);
    }
    EventData event(long activityId, long sequence) {
        return jdbc.query("""
                select event_sequence, kind, quantity_delta from activity_inventory_event
                 where activity_id = ? and event_sequence = ?
                """, (rs, n) -> new EventData(rs.getLong(1), ActivityInventoryLedger.Kind.valueOf(rs.getString(2)), rs.getInt(3)),
                activityId, sequence).stream().findFirst().orElse(null);
    }
    record EventData(long sequence, ActivityInventoryLedger.Kind kind, int quantityDelta) {}
    private long value(String sql, long id) { Long x = jdbc.queryForObject(sql, Long.class, id); return x == null ? 0 : x; }
}
