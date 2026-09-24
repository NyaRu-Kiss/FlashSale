package com.flashsale.common.messaging;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.client.producer.SendStatus;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** RocketMQ transport. Topic is configured once per producer service; event type is the tag. */
public final class RocketMqMessageTransport implements MessageTransport, AutoCloseable {
    private final DefaultMQProducer producer;
    private final String topic;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public RocketMqMessageTransport(String producerGroup, String namesrvAddr, String topic,
                                    com.fasterxml.jackson.databind.ObjectMapper json) {
        if (producerGroup == null || producerGroup.isBlank() || namesrvAddr == null || namesrvAddr.isBlank()
                || topic == null || topic.isBlank()) throw new IllegalArgumentException("RocketMQ configuration required");
        this.topic = topic;
        this.json = Objects.requireNonNull(json).copy()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.producer = new DefaultMQProducer(producerGroup);
        this.producer.setNamesrvAddr(namesrvAddr);
    }

    public synchronized void start() throws Exception { producer.start(); }

    @Override public void send(MessageEnvelope message) throws Exception {
        var mqMessage = rocketMessage(message);
        var result = producer.send(mqMessage);
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("RocketMQ send was not confirmed: " + (result == null ? "null" : result.getSendStatus()));
        }
    }

    Message rocketMessage(MessageEnvelope message) throws Exception {
        Objects.requireNonNull(message);
        String body = json.writeValueAsString(message);
        Message mqMessage = new Message(topic, message.eventType(), body.getBytes(StandardCharsets.UTF_8));
        mqMessage.setKeys(message.idempotencyKey());
        mqMessage.putUserProperty("event_id", message.eventId().toString());
        mqMessage.putUserProperty("trace_id", message.traceId() == null ? "" : message.traceId());
        return mqMessage;
    }

    @Override public synchronized void close() { producer.shutdown(); }
}
