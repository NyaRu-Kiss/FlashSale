package com.flashsale.common.messaging;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** RocketMQ transport. Topic is configured once per producer service; event type is the tag. */
public final class RocketMqMessageTransport implements MessageTransport, AutoCloseable {
    private final DefaultMQProducer producer;
    private final String topic;

    public RocketMqMessageTransport(String producerGroup, String namesrvAddr, String topic) {
        if (producerGroup == null || producerGroup.isBlank() || namesrvAddr == null || namesrvAddr.isBlank()
                || topic == null || topic.isBlank()) throw new IllegalArgumentException("RocketMQ configuration required");
        this.topic = topic;
        this.producer = new DefaultMQProducer(producerGroup);
        this.producer.setNamesrvAddr(namesrvAddr);
    }

    public synchronized void start() throws Exception { producer.start(); }

    @Override public void send(MessageEnvelope message) throws Exception {
        Objects.requireNonNull(message);
        String body = message.payload().toString();
        Message mqMessage = new Message(topic, message.eventType(), body.getBytes(StandardCharsets.UTF_8));
        mqMessage.setKeys(message.idempotencyKey());
        mqMessage.putUserProperty("event_id", message.eventId().toString());
        mqMessage.putUserProperty("trace_id", message.traceId() == null ? "" : message.traceId());
        producer.send(mqMessage);
    }

    @Override public synchronized void close() { producer.shutdown(); }
}
