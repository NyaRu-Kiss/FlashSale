package com.flashsale.common.messaging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsumerMessageHandlerTraceTest {
    @AfterEach void clear() { MDC.clear(); }

    @Test void businessHandlerSeesMessageCorrelationFields() {
        var idempotency = new InMemoryConsumerIdempotency();
        var message = new MessageEnvelope(UUID.randomUUID(), "TEST", 1, "TEST:1", "test", "ORDER", "1",
                Instant.now(), "trace-mq", Map.of("user_id", 7L, "order_id", 1L));
        var handler = new ConsumerMessageHandler(idempotency, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                java.time.Duration.ofMinutes(1), m -> {
                    assertEquals("trace-mq", MDC.get("trace_id"));
                    assertEquals("TEST:1", MDC.get("idempotency_key"));
                    assertEquals("7", MDC.get("user_id"));
                });
        assertEquals(ConsumerMessageHandler.HandleResult.SUCCEEDED, handler.handle(message));
    }

    private static final class InMemoryConsumerIdempotency implements ConsumerIdempotencyPort {
        public boolean tryStart(String key, String eventId, Instant now, java.time.Duration timeout) { return true; }
        public void markSucceeded(String key, Instant now) { }
        public void markFailed(String key, Instant now, String error) { }
    }
}
