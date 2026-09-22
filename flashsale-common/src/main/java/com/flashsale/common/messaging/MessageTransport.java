package com.flashsale.common.messaging;

/** Reliable-message transport boundary. Implementations may target RocketMQ or a test bus. */
public interface MessageTransport {
    void send(MessageEnvelope message) throws Exception;
}
