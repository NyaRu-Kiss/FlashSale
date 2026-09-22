package com.flashsale.common.messaging;

import java.time.Instant;
import java.util.UUID;

public record OutboxRecord(long id, UUID eventId, String idempotencyKey, MessageEnvelope message,
                           OutboxStatus status, int attemptCount, Instant availableAt, Instant lockedUntil) {
    public boolean dispatchable(Instant now) {
        return (status == OutboxStatus.PENDING || status == OutboxStatus.FAILED)
                && !availableAt.isAfter(now)
                && (lockedUntil == null || lockedUntil.isBefore(now));
    }
}
