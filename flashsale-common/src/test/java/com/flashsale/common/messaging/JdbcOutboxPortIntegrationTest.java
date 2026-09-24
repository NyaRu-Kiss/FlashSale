package com.flashsale.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
class JdbcOutboxPortIntegrationTest {
    @Test void batchesAndMarksSentOnlyForCurrentLease() {
        var source = new DriverManagerDataSource(System.getenv("TEST_POSTGRES_URL"),
                System.getenv().getOrDefault("TEST_POSTGRES_USER", "flashsale"),
                System.getenv().getOrDefault("TEST_POSTGRES_PASSWORD", "flashsale"));
        var jdbc = new JdbcTemplate(source);
        jdbc.update("DELETE FROM product_outbox WHERE event_type='TEST'");
        for (int i = 0; i < 3; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO product_outbox (event_id,event_type,idempotency_key,aggregate_type,aggregate_id,payload)"
                    + " VALUES (?, 'TEST', ?, 'PRODUCT', '1', '{}'::jsonb)", id, "product:" + id);
        }
        var port = new JdbcOutboxPort(jdbc, new ObjectMapper(), new DataSourceTransactionManager(source), "product_outbox", 3);
        Instant now = Instant.now().plusSeconds(1);
        var first = port.claimDue(now, 2, Duration.ofSeconds(5));
        assertEquals(2, first.size());
        assertEquals(3, port.backlog(now).pending());
        assertEquals(1, port.claimDue(now, 2, Duration.ofSeconds(5)).size());
        assertTrue(port.markSent(first.getFirst(), now.plusSeconds(1)));
        assertFalse(port.markSent(first.getFirst(), now.plusSeconds(1)));
        assertEquals("SENT", jdbc.queryForObject("SELECT status::text FROM product_outbox WHERE id=?",
                String.class, first.getFirst().id()));
        assertEquals(2, port.backlog(now).pending());
        jdbc.update("DELETE FROM product_outbox WHERE event_type='TEST'");
    }

    @Test void allServiceTablesClaimLeaseRetryAndStopAtMaximum() {
        var source = new DriverManagerDataSource(System.getenv("TEST_POSTGRES_URL"),
                System.getenv().getOrDefault("TEST_POSTGRES_USER", "flashsale"),
                System.getenv().getOrDefault("TEST_POSTGRES_PASSWORD", "flashsale"));
        var jdbc = new JdbcTemplate(source);
        var manager = new DataSourceTransactionManager(source);
        Instant now = Instant.now().plusSeconds(1);
        for (String service : new String[] {"order", "coupon", "inventory", "payment", "product", "activity"}) {
            String table = service + "_outbox";
            jdbc.update("DELETE FROM " + table + " WHERE event_type='TEST'");
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO " + table + " (event_id,event_type,idempotency_key,aggregate_type,aggregate_id,payload,trace_id)"
                    + " VALUES (?, 'TEST', ?, 'TEST', '1', '{}'::jsonb, 'trace')", id, service + ":" + id);
            var first = new JdbcOutboxPort(jdbc, new ObjectMapper(), manager, table, 2);
            var second = new JdbcOutboxPort(jdbc, new ObjectMapper(), manager, table, 2);
            var claimed = first.claimDue(now, 1, Duration.ofSeconds(5));
            assertEquals(1, claimed.size(), service);
            assertEquals(id, claimed.getFirst().eventId());
            assertEquals(service, claimed.getFirst().message().producer());
            assertTrue(second.claimDue(now.plusSeconds(1), 1, Duration.ofSeconds(5)).isEmpty());
            var reclaimed = second.claimDue(now.plusSeconds(6), 1, Duration.ofSeconds(5)).getFirst();
            assertFalse(first.markSent(claimed.getFirst(), now.plusSeconds(7)));
            assertTrue(second.markFailed(reclaimed, now.plusSeconds(8), "failed"));
            assertTrue(first.claimDue(now.plusSeconds(9), 1, Duration.ofSeconds(5)).isEmpty());
            assertEquals("FAILED", jdbc.queryForObject("SELECT status::text FROM " + table + " WHERE event_id=?", String.class, id));
            jdbc.update("DELETE FROM " + table + " WHERE event_id=?", id);
        }
    }
}
