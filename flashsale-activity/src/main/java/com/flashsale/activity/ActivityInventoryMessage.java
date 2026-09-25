package com.flashsale.activity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Canonical RocketMQ payload for an activity inventory event. */
record ActivityInventoryMessage(@JsonProperty("event_id") @JsonAlias("eventId") UUID eventId,
                                @JsonProperty("idempotency_key") @JsonAlias("idempotencyKey") String idempotencyKey,
                                @JsonProperty("activity_id") @JsonAlias("activityId") long activityId,
                                long sequence,
                                ActivityInventoryLedger.Kind kind,
                                int quantity,
                                @JsonProperty("trace_id") @JsonAlias("traceId") String traceId) {
    ActivityInventoryMessage {
        if (eventId == null || idempotencyKey == null || idempotencyKey.isBlank()
                || activityId <= 0 || sequence <= 0 || quantity <= 0 || kind == null) {
            throw new IllegalArgumentException("INVALID_ACTIVITY_INVENTORY_MESSAGE");
        }
    }
}
