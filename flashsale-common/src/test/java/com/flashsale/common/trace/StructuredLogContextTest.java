package com.flashsale.common.trace;

import com.flashsale.common.messaging.MessageEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StructuredLogContextTest {
    @AfterEach void clear() { MDC.clear(); TraceContext.clear(); }

    @Test void capturesAndRestoresAcrossThreadBoundary() throws Exception {
        try (var ignored = StructuredLogContext.open(Map.of("trace_id", "t-1", "user_id", "42"))) {
            var seen = new AtomicReference<Map<String, String>>();
            Thread thread = new Thread(StructuredLogContext.wrap(() -> seen.set(MDC.getCopyOfContextMap())));
            thread.start(); thread.join();
            assertEquals("t-1", seen.get().get("trace_id"));
            assertEquals("42", seen.get().get("user_id"));
        }
        assertNull(MDC.get("trace_id"));
    }

    @Test void messageContextContainsRequiredCorrelationFields() {
        UUID eventId = UUID.randomUUID();
        var message = new MessageEnvelope(eventId, "ORDER_CREATED", 1, "ORDER:1", "order",
                "ORDER", "1", Instant.now(), "trace-1", Map.of("user_id", 42L, "order_id", 1L));
        try (var ignored = StructuredLogContext.openMessage(message)) {
            assertEquals("trace-1", MDC.get("trace_id"));
            assertEquals(eventId.toString(), MDC.get("event_id"));
            assertEquals("ORDER:1", MDC.get("idempotency_key"));
            assertEquals("42", MDC.get("user_id"));
            assertEquals("1", MDC.get("order_id"));
        }
    }
}
