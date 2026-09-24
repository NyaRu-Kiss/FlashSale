package com.flashsale.common.messaging;

import java.time.Duration;
import java.time.Instant;

public final class OutboxStateMachine {
    private final BackoffPolicy backoff;

    public OutboxStateMachine(BackoffPolicy backoff) { this.backoff = backoff; }

    public Instant nextAttempt(OutboxRecord record, Instant now) {
        return now.plus(backoff.delay(record.attemptCount()));
    }

    public Duration lease(Duration requested) {
        if (requested.isNegative() || requested.isZero()) throw new IllegalArgumentException("lease must be positive");
        return requested;
    }
}
