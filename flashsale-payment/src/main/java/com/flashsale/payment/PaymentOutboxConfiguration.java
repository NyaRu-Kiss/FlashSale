package com.flashsale.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.messaging.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flashsale.outbox.enabled", havingValue = "true")
final class PaymentOutboxConfiguration {
    @Bean OutboxPort paymentOutboxPort(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager,
            @Value("${flashsale.outbox.max-attempts:8}") int maxAttempts) {
        return OutboxDispatchConfiguration.port(jdbc, json, manager, "payment_outbox", maxAttempts);
    }
    @Bean(destroyMethod = "close") RocketMqMessageTransport paymentOutboxTransport(@Value("${flashsale.outbox.producer-group:flashsale-payment}") String group,
            @Value("${flashsale.outbox.namesrv-addr:rocketmq-namesrv:9876}") String namesrv, @Value("${flashsale.outbox.topic:FLASHSALE_EVENTS}") String topic, ObjectMapper json) throws Exception {
        var transport = new RocketMqMessageTransport(group, namesrv, topic, json); transport.start(); return transport;
    }
    @Bean OutboxDispatcher paymentOutboxDispatcher(OutboxPort port, MessageTransport transport,
            @Value("${flashsale.outbox.batch-size:100}") int batch, @Value("${flashsale.outbox.lease:PT30S}") Duration lease,
            @Value("${flashsale.outbox.max-attempts:8}") int max, @Value("${flashsale.outbox.backoff-initial:PT1S}") Duration initial,
            @Value("${flashsale.outbox.backoff-maximum:PT5M}") Duration maximum) { return OutboxDispatchConfiguration.dispatcher(port, transport, batch, lease, max, initial, maximum); }
    @Bean OutboxDispatchTask paymentOutboxDispatchTask(OutboxDispatcher dispatcher, MessagingMetrics metrics) { return OutboxDispatchConfiguration.task(dispatcher, metrics); }
}
