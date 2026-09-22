package com.flashsale.common.messaging;

import java.time.Duration;
import java.time.Instant;

public interface ConsumerIdempotencyPort {
    /** Atomically creates PROCESSING or reclaims an expired PROCESSING record. */
    boolean tryStart(String idempotencyKey, String eventId, Instant now, Duration processingTimeout);
    void markSucceeded(String idempotencyKey, Instant now);
    void markFailed(String idempotencyKey, Instant now, String error);
}
