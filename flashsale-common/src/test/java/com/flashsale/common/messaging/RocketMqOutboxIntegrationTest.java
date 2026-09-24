package com.flashsale.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_ROCKETMQ_NAMESRV", matches = ".+")
class RocketMqOutboxIntegrationTest {
    @Test void confirmedBrokerSendMarksOutboxSent() throws Exception {
        var outbox = new InMemoryOutbox();
        var event = new MessageEnvelope(UUID.randomUUID(), "R16_TEST", 1, "r16:" + UUID.randomUUID(),
                "test", "TEST", "1", Instant.now(), "trace-r16", Map.of("value", 1));
        var record = outbox.add(event, Instant.EPOCH);
        try (var transport = new RocketMqMessageTransport("r16-test-" + UUID.randomUUID(),
                System.getenv("TEST_ROCKETMQ_NAMESRV"), "FLASHSALE_EVENTS",
                new ObjectMapper().findAndRegisterModules())) {
            transport.start();
            var dispatcher = new OutboxDispatcher(outbox, transport,
                    new OutboxStateMachine(new BackoffPolicy(Duration.ofSeconds(1), Duration.ofMinutes(1))),
                    Clock.systemUTC(), 10, Duration.ofSeconds(30), 3);
            assertEquals(1, dispatcher.dispatchOnce().sent());
            assertEquals(OutboxStatus.SENT, outbox.find(record.id()).orElseThrow().status());
        }
    }
}
