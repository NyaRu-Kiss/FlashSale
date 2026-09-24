package com.flashsale.common.messaging;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxStateMachineTest {
    @Test
    void backoffIsExponentialAndCapped() {
        BackoffPolicy policy = new BackoffPolicy(Duration.ofSeconds(1), Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(1), policy.delay(0));
        assertEquals(Duration.ofSeconds(2), policy.delay(1));
        assertEquals(Duration.ofSeconds(4), policy.delay(2));
        assertEquals(Duration.ofSeconds(5), policy.delay(3));
    }

    @Test
    void failedRecordGetsNextAttemptAfterBackoff() {
        MessageEnvelope message = new MessageEnvelope(UUID.randomUUID(), "TEST", 1, "TEST_1", "test", "X", "1", Instant.now(), "trace", Map.of());
        OutboxRecord record = new OutboxRecord(1, message.eventId(), message.idempotencyKey(), message, OutboxStatus.FAILED, 1, Instant.EPOCH, null);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertEquals(now.plusSeconds(2), new OutboxStateMachine(new BackoffPolicy(Duration.ofSeconds(1), Duration.ofSeconds(5))).nextAttempt(record, now));
    }
}
