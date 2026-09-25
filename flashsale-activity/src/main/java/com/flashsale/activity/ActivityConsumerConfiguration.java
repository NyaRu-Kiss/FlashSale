package com.flashsale.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.messaging.ConsumerMessageHandler;
import com.flashsale.common.messaging.JdbcConsumerMessageHandler;
import com.flashsale.common.messaging.MessageEnvelope;
import com.flashsale.common.messaging.RocketMqConsumerAdapter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flashsale.consumer.enabled", havingValue = "true")
final class ActivityConsumerConfiguration {
    @Bean(destroyMethod = "close") RocketMqConsumerAdapter activityConsumer(
            JdbcTemplate jdbc, PlatformTransactionManager manager, ActivityInventoryConsumer consumer,
            ObjectMapper json, @Value("${flashsale.consumer.group:flashsale-activity-consumer}") String group,
            @Value("${flashsale.consumer.namesrv-addr:rocketmq-namesrv:9876}") String namesrv,
            @Value("${flashsale.consumer.topic:FLASHSALE_EVENTS}") String topic,
            @Value("${flashsale.consumer.processing-timeout:PT5M}") Duration timeout) throws Exception {
        ConsumerMessageHandler.BusinessHandler business = message -> consumer.consume(decode(message, json));
        var durable = new JdbcConsumerMessageHandler(jdbc, manager, "activity_message_idempotency", timeout, business);
        var adapter = new RocketMqConsumerAdapter(group, namesrv, topic,
                "ACTIVITY_INVENTORY_RESERVE || ACTIVITY_INVENTORY_RELEASE",
                body -> decodeEnvelope(body, json), message -> durable.handle(message, message.eventId().toString()));
        adapter.start();
        return adapter;
    }

    private static ActivityInventoryMessage decode(MessageEnvelope message, ObjectMapper json) throws Exception {
        return json.convertValue(message.payload(), ActivityInventoryMessage.class);
    }
    private static MessageEnvelope decodeEnvelope(byte[] body, ObjectMapper json) {
        try { return json.readValue(body, MessageEnvelope.class); }
        catch (Exception e) { throw new IllegalArgumentException("INVALID_ACTIVITY_MESSAGE", e); }
    }
}
