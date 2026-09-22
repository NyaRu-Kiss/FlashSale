package com.flashsale.common.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MessageEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        String idempotencyKey,
        String producer,
        String aggregateType,
        String aggregateId,
        Instant occurredAt,
        String traceId,
        Map<String, Object> payload) {
    public MessageEnvelope {
        if (eventId == null || eventType == null || idempotencyKey == null || producer == null || payload == null) {
            throw new IllegalArgumentException("message envelope required fields are missing");
        }
        payload = Map.copyOf(payload);
    }
}
