package com.flashsale.common.messaging;

import java.time.Duration;
import java.time.Instant;

public final class ConsumerIdempotencyStateMachine {
    private final Duration processingTimeout;

    public ConsumerIdempotencyStateMachine(Duration processingTimeout) {
        if (processingTimeout.isNegative() || processingTimeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        this.processingTimeout = processingTimeout;
    }

    public boolean canStart(ConsumerIdempotencyRecord record, Instant now) {
        return record == null || record.status() == ConsumerStatus.FAILED || record.processingExpired(now, processingTimeout);
    }

    public Duration processingTimeout() { return processingTimeout; }
}
