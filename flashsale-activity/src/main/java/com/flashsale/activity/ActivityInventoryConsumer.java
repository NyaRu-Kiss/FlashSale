package com.flashsale.activity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies durable inventory projection strictly in event sequence order. */
@Service
class ActivityInventoryConsumer {
    private final ActivityInventoryEventRepository events;
    private final JdbcTemplate jdbc;
    ActivityInventoryConsumer(ActivityInventoryEventRepository events, JdbcTemplate jdbc) { this.events = events; this.jdbc = jdbc; }

    @Transactional
    boolean consume(long activityId, long sequence) {
        long checkpoint = events.checkpoint(activityId);
        if (sequence <= checkpoint) return false;
        if (sequence != checkpoint + 1) throw new IllegalArgumentException("CHECKPOINT_GAP");
        ActivityInventoryEventRepository.EventData event = events.event(activityId, sequence);
        if (event == null) throw new IllegalArgumentException("EVENT_NOT_FOUND");
        int changed = jdbc.update("""
                update marketing_activity set available_stock = available_stock + ?, version = version + 1
                 where id = ? and available_stock + ? between 0 and initial_stock
                """, event.quantityDelta(), activityId, event.quantityDelta());
        if (changed != 1) throw new IllegalArgumentException("ACTIVITY_INVENTORY_PROJECTION_INVALID");
        events.advanceCheckpoint(activityId, sequence);
        return true;
    }

    /** MQ adapter entrypoint. Sequence ordering remains the source of truth; duplicate event IDs are harmless. */
    @Transactional
    boolean consume(ActivityInventoryMessage message) {
        if (message == null) throw new IllegalArgumentException("INVALID_ACTIVITY_INVENTORY_MESSAGE");
        return consume(message.activityId(), message.sequence());
    }
}
