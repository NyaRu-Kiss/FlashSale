package com.flashsale.common.messaging;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.flashsale.common.trace.StructuredLogContext;

/** RocketMQ consumer adapter. Decoder belongs to the service because payload schemas are service-owned. */
public final class RocketMqConsumerAdapter implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(RocketMqConsumerAdapter.class);
    private final DefaultMQPushConsumer consumer;

    public RocketMqConsumerAdapter(String consumerGroup, String namesrvAddr, String topic, String selectorExpression,
                                   Function<byte[], MessageEnvelope> decoder, ConsumerMessageHandler handler) {
        this(consumerGroup, namesrvAddr, topic, selectorExpression, decoder, envelope -> handler.handle(envelope));
    }

    public RocketMqConsumerAdapter(String consumerGroup, String namesrvAddr, String topic, String selectorExpression,
                                   Function<byte[], MessageEnvelope> decoder,
                                   Function<MessageEnvelope, ConsumerMessageHandler.HandleResult> handler) {
        if (consumerGroup == null || consumerGroup.isBlank() || namesrvAddr == null || namesrvAddr.isBlank()
                || topic == null || topic.isBlank()) throw new IllegalArgumentException("RocketMQ configuration required");
        Objects.requireNonNull(decoder); Objects.requireNonNull(handler);
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        try {
            consumer.subscribe(topic, selectorExpression == null || selectorExpression.isBlank() ? "*" : selectorExpression);
        } catch (Exception e) { throw new IllegalArgumentException("invalid RocketMQ subscription", e); }
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (MessageExt message : messages) {
                try {
                    MessageEnvelope envelope = decoder.apply(message.getBody());
                    try (StructuredLogContext ignored = StructuredLogContext.openMessage(envelope)) {
                    if (handler.apply(envelope) == ConsumerMessageHandler.HandleResult.RETRY)
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                } catch (RuntimeException error) {
                    LOG.error("RocketMQ consumer failed; message will be retried topic={} keys={} reconsumeTimes={}",
                            message.getTopic(), message.getKeys(), message.getReconsumeTimes(), error);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
    }

    public void start() throws Exception { consumer.start(); }
    @Override public void close() { consumer.shutdown(); }
}
