package com.flashsale.common.messaging;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerIdempotencyStateMachineTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final ConsumerIdempotencyStateMachine machine = new ConsumerIdempotencyStateMachine(Duration.ofMinutes(5));

    @Test
    void newFailedAndExpiredProcessingRecordsCanStart() {
        assertTrue(machine.canStart(null, now));
        assertTrue(machine.canStart(new ConsumerIdempotencyRecord("k", "e", ConsumerStatus.FAILED, now, now, "error"), now));
        assertTrue(machine.canStart(new ConsumerIdempotencyRecord("k", "e", ConsumerStatus.PROCESSING, now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(6)), null), now));
    }

    @Test
    void succeededOrLiveProcessingRecordsCannotStart() {
        assertFalse(machine.canStart(new ConsumerIdempotencyRecord("k", "e", ConsumerStatus.SUCCEEDED, now, now, null), now));
        assertFalse(machine.canStart(new ConsumerIdempotencyRecord("k", "e", ConsumerStatus.PROCESSING, now, now, null), now));
    }
}
