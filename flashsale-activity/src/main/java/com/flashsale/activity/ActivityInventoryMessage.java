package com.flashsale.activity;

import java.util.UUID;

/** Canonical RocketMQ payload for an activity inventory event. */
record ActivityInventoryMessage(UUID eventId, String idempotencyKey, long activityId, long sequence,
                                ActivityInventoryLedger.Kind kind, int quantity, String traceId) {
    ActivityInventoryMessage {
        if (eventId == null || idempotencyKey == null || idempotencyKey.isBlank()
                || activityId <= 0 || sequence <= 0 || quantity <= 0 || kind == null) {
            throw new IllegalArgumentException("INVALID_ACTIVITY_INVENTORY_MESSAGE");
        }
    }
}
