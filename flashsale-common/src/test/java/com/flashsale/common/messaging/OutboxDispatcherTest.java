package com.flashsale.common.messaging;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OutboxDispatcherTest {
    private MessageEnvelope message() {
        return new MessageEnvelope(UUID.randomUUID(), "ORDER_PAID", 1, "ORDER_PAID_1", "order",
                "ORDER", "O1", Instant.EPOCH, "trace", Map.of("order", "O1"));
    }
    @Test void sendsClaimedBatchAndMarksSent() {
        var outbox = new InMemoryOutbox(); var transport = new InMemoryMessageTransport();
        var record = outbox.add(message(), Instant.EPOCH);
        var dispatcher = new OutboxDispatcher(outbox, transport,
                new OutboxStateMachine(new BackoffPolicy(Duration.ofSeconds(1), Duration.ofSeconds(10))),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), 10, Duration.ofMinutes(1), 3);
        assertEquals(new OutboxDispatcher.DispatchReport(1, 1, 0, 0), dispatcher.dispatchOnce());
        assertEquals(OutboxStatus.SENT, outbox.find(record.id()).orElseThrow().status());
    }
    @Test void failedSendIsRetriedWithBackoff() {
        var outbox = new InMemoryOutbox(); var transport = new InMemoryMessageTransport(); transport.fail(true);
        var record = outbox.add(message(), Instant.EPOCH);
        var dispatcher = new OutboxDispatcher(outbox, transport,
                new OutboxStateMachine(new BackoffPolicy(Duration.ofSeconds(1), Duration.ofSeconds(10))),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), 10, Duration.ofMinutes(1), 3);
        assertEquals(1, dispatcher.dispatchOnce().failed());
        var failed = outbox.find(record.id()).orElseThrow();
        assertEquals(OutboxStatus.FAILED, failed.status());
        assertEquals(1, failed.attemptCount());
        assertEquals(Instant.parse("2026-01-01T00:00:04Z"), failed.availableAt());
    }
}
