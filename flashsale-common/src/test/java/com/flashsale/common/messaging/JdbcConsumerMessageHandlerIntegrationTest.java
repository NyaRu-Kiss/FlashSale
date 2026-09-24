package com.flashsale.common.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
class JdbcConsumerMessageHandlerIntegrationTest {
    private final DriverManagerDataSource source = new DriverManagerDataSource(System.getenv("TEST_POSTGRES_URL"),
            System.getenv().getOrDefault("TEST_POSTGRES_USER", "flashsale"),
            System.getenv().getOrDefault("TEST_POSTGRES_PASSWORD", "flashsale"));
    private final JdbcTemplate jdbc = new JdbcTemplate(source);

    @org.junit.jupiter.api.BeforeAll
    static void schema() {
        var source = new DriverManagerDataSource(System.getenv("TEST_POSTGRES_URL"),
                System.getenv().getOrDefault("TEST_POSTGRES_USER", "flashsale"),
                System.getenv().getOrDefault("TEST_POSTGRES_PASSWORD", "flashsale"));
        new JdbcTemplate(source).execute("create table if not exists inventory_message_idempotency_test(key varchar(160) primary key)");
    }

    @Test void commitsBusinessAndSuccessBeforeAckAndSkipsRedelivery() {
        String key = "STOCK_TEST_" + UUID.randomUUID();
        MessageEnvelope message = message(key);
        AtomicBoolean fail = new AtomicBoolean(false);
        var handler = handler(m -> {
            jdbc.update("insert into inventory_message_idempotency_test(key) values (?)", key);
            if (fail.get()) throw new IllegalStateException("boom");
        });
        try {
            assertEquals(ConsumerMessageHandler.HandleResult.SUCCEEDED, handler.handle(message, "broker-1"));
            assertEquals(ConsumerMessageHandler.HandleResult.DUPLICATE, handler.handle(message, "broker-2"));
            assertEquals(1, jdbc.queryForObject("select count(*) from inventory_message_idempotency_test where key=?", Integer.class, key));
            assertEquals("SUCCEEDED", status(key));
            assertEquals("broker-1", jdbc.queryForObject("select message_id from inventory_message_idempotency where idempotency_key=?", String.class, key));
        } finally { cleanup(key); }
    }

    @Test void failureRollsBackBusinessAndRetriesFromFailed() {
        String key = "STOCK_TEST_" + UUID.randomUUID();
        MessageEnvelope message = message(key);
        AtomicBoolean fail = new AtomicBoolean(true);
        var handler = handler(m -> {
            jdbc.update("insert into inventory_message_idempotency_test(key) values (?)", key);
            if (fail.get()) throw new IllegalStateException("boom");
        });
        try {
            assertEquals(ConsumerMessageHandler.HandleResult.RETRY, handler.handle(message, "broker-1"));
            assertEquals("FAILED", status(key));
            assertEquals(0, jdbc.queryForObject("select count(*) from inventory_message_idempotency_test where key=?", Integer.class, key));
            fail.set(false);
            assertEquals(ConsumerMessageHandler.HandleResult.SUCCEEDED, handler.handle(message, "broker-2"));
            assertEquals(2, jdbc.queryForObject("select attempt_count from inventory_message_idempotency where idempotency_key=?", Integer.class, key));
        } finally { cleanup(key); }
    }

    @Test void activeProcessingRetriesAndExpiredProcessingRecovers() {
        String key = "STOCK_TEST_" + UUID.randomUUID();
        MessageEnvelope message = message(key);
        try {
            jdbc.update("insert into inventory_message_idempotency(idempotency_key,event_id,event_type,aggregate_id,status,started_at,message_id) values (?,?,?,?,'PROCESSING',now(),?)",
                    key, message.eventId(), message.eventType(), message.aggregateId(), "broker-1");
            var handler = handler(m -> jdbc.update("insert into inventory_message_idempotency_test(key) values (?)", key));
            assertEquals(ConsumerMessageHandler.HandleResult.RETRY, handler.handle(message, "broker-2"));
            jdbc.update("update inventory_message_idempotency set started_at=now()-interval '10 minutes' where idempotency_key=?", key);
            assertEquals(ConsumerMessageHandler.HandleResult.SUCCEEDED, handler.handle(message, "broker-2"));
            assertEquals(2, jdbc.queryForObject("select attempt_count from inventory_message_idempotency where idempotency_key=?", Integer.class, key));
        } finally { cleanup(key); }
    }

    private JdbcConsumerMessageHandler handler(ConsumerMessageHandler.BusinessHandler business) {
        return new JdbcConsumerMessageHandler(jdbc, new DataSourceTransactionManager(source),
                "inventory_message_idempotency", Duration.ofMinutes(5), business);
    }
    private MessageEnvelope message(String key) {
        return new MessageEnvelope(UUID.randomUUID(), "STOCK_TEST", 1, key, "test", "STOCK", "1", Instant.now(), "trace", Map.of());
    }
    private String status(String key) {
        return jdbc.queryForObject("select status::text from inventory_message_idempotency where idempotency_key=?", String.class, key);
    }
    private void cleanup(String key) {
        jdbc.update("delete from inventory_message_idempotency_test where key=?", key);
        jdbc.update("delete from inventory_message_idempotency where idempotency_key=?", key);
    }
}
