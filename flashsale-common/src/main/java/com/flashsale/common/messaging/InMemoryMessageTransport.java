package com.flashsale.common.messaging;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryMessageTransport implements MessageTransport {
    private final List<MessageEnvelope> sent = new ArrayList<>();
    private boolean fail;
    public synchronized void fail(boolean value) { fail = value; }
    @Override public synchronized void send(MessageEnvelope message) {
        if (fail) throw new IllegalStateException("transport unavailable");
        sent.add(message);
    }
    public synchronized List<MessageEnvelope> sent() { return List.copyOf(sent); }
}
