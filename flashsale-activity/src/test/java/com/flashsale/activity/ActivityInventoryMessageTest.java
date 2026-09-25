package com.flashsale.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityInventoryMessageTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsOutboxSnakeCasePayload() {
        UUID eventId = UUID.randomUUID();
        ActivityInventoryMessage message = json.convertValue(Map.of(
                "event_id", eventId.toString(),
                "idempotency_key", "ORDER:ACTIVITY_INVENTORY:" + eventId,
                "activity_id", 5,
                "sequence", 1,
                "kind", "RESERVE",
                "quantity", 1,
                "trace_id", "trace-1"), ActivityInventoryMessage.class);

        assertEquals(eventId, message.eventId());
        assertEquals(5, message.activityId());
        assertEquals(ActivityInventoryLedger.Kind.RESERVE, message.kind());
    }
}
