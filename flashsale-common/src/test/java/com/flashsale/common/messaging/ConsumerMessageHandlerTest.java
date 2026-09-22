package com.flashsale.common.messaging;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConsumerMessageHandlerTest {
    private MessageEnvelope message(String key) {
        return new MessageEnvelope(UUID.randomUUID(), "ORDER_PAID", 1, key, "payment", "ORDER", "1",
                Instant.EPOCH, "trace", Map.of("order", "1"));
    }
    @Test void duplicateIsSkippedAfterSuccess() {
        var store = new InMemoryConsumerIdempotency(); int[] calls = {0};
        var handler = new ConsumerMessageHandler(store, Clock.systemUTC(), Duration.ofMinutes(5), m -> calls[0]++);
        var message = message("ORDER_PAID_1");
        assertEquals(ConsumerMessageHandler.HandleResult.SUCCEEDED, handler.handle(message));
        assertEquals(ConsumerMessageHandler.HandleResult.DUPLICATE, handler.handle(message));
        assertEquals(1, calls[0]);
    }
    @Test void failedMessageCanBeRetried() {
        var store = new InMemoryConsumerIdempotency(); boolean[] fail = {true};
        var handler = new ConsumerMessageHandler(store, Clock.systemUTC(), Duration.ofMinutes(5), m -> { if (fail[0]) throw new IllegalStateException("boom"); });
        var message = message("ORDER_CANCEL_1");
        assertEquals(ConsumerMessageHandler.HandleResult.RETRY, handler.handle(message));
        fail[0] = false;
        assertEquals(ConsumerMessageHandler.HandleResult.SUCCEEDED, handler.handle(message));
    }
    @Test void processingTimeoutCanBeReclaimed() {
        var store = new InMemoryConsumerIdempotency();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertTrue(store.tryStart("K", "E", now, Duration.ofMinutes(5)));
        assertTrue(store.tryStart("K", "E", now.plus(Duration.ofMinutes(6)), Duration.ofMinutes(5)));
    }
}
