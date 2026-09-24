package com.flashsale.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RocketMqMessageTransportTest {
    @Test void rocketMessageCarriesFullEnvelopeAndRoutingMetadata() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var eventId = UUID.randomUUID();
        var envelope = new MessageEnvelope(eventId, "ORDER_PAID", 1, "order:paid:1", "order", "ORDER", "1",
                Instant.parse("2026-01-01T00:00:00Z"), "trace-1", Map.of("amount", 100));
        var transport = new RocketMqMessageTransport("test-producer", "localhost:9876", "FLASHSALE_EVENTS", json);
        var message = transport.rocketMessage(envelope);
        assertEquals("FLASHSALE_EVENTS", message.getTopic());
        assertEquals("ORDER_PAID", message.getTags());
        assertEquals("order:paid:1", message.getKeys());
        assertEquals(eventId.toString(), message.getUserProperty("event_id"));
        assertEquals("trace-1", message.getUserProperty("trace_id"));
        var body = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertEquals(eventId.toString(), body.get("eventId").asText());
        assertEquals("order", body.get("producer").asText());
        assertEquals("2026-01-01T00:00:00Z", body.get("occurredAt").asText());
        assertEquals(100, body.get("payload").get("amount").asInt());
    }
}
