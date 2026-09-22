package com.flashsale.common.messaging;

import java.time.Instant;

public record ConsumerIdempotencyRecord(
        String idempotencyKey,
        String eventId,
        ConsumerStatus status,
        Instant startedAt,
        Instant updatedAt,
        String error) {
    public boolean processingExpired(Instant now, java.time.Duration timeout) {
        return status == ConsumerStatus.PROCESSING && updatedAt.plus(timeout).isBefore(now);
    }
}
