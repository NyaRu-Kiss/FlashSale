package com.flashsale.common.messaging;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MessagingContractTest {
    @Test
    void envelopeCopiesPayloadAndOutboxChecksLease() {
        MessageEnvelope envelope = new MessageEnvelope(UUID.randomUUID(), "TEST", 1, "TEST_1", "test", "X", "1", Instant.now(), "trace", Map.of("a", 1));
        OutboxRecord record = new OutboxRecord(1, envelope.eventId(), envelope.idempotencyKey(), envelope, OutboxStatus.PENDING, 0, Instant.EPOCH, null);
        assertTrue(record.dispatchable(Instant.now()));
        assertThrows(UnsupportedOperationException.class, () -> envelope.payload().put("b", 2));
    }
}
