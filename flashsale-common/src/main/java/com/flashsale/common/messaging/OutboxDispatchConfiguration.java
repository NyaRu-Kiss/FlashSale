package com.flashsale.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Shared opt-in production wiring; each service supplies its own table name. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flashsale.outbox.enabled", havingValue = "true")
public class OutboxDispatchConfiguration {
    @Bean
    public MessagingMetrics messagingMetrics(MeterRegistry registry) { return new MessagingMetrics(registry); }

    public static JdbcOutboxPort port(JdbcTemplate jdbc, ObjectMapper json,
                                      PlatformTransactionManager manager, String table, int maxAttempts) {
        return new JdbcOutboxPort(jdbc, json, manager, table, maxAttempts);
    }

    public static OutboxDispatcher dispatcher(OutboxPort port, MessageTransport transport,
                                              int batchSize, Duration lease, int maxAttempts,
                                              Duration initial, Duration maximum) {
        return new OutboxDispatcher(port, transport,
                new OutboxStateMachine(new BackoffPolicy(initial, maximum)), Clock.systemUTC(),
                batchSize, lease, maxAttempts);
    }

    public static OutboxDispatchTask task(OutboxDispatcher dispatcher, MessagingMetrics metrics) {
        return new OutboxDispatchTask(dispatcher, metrics);
    }
}
