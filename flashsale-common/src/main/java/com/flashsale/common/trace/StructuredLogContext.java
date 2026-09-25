package com.flashsale.common.trace;

import org.slf4j.MDC;

import com.flashsale.common.messaging.MessageEnvelope;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import java.util.Map;

/** Scoped MDC fields for structured logs and cross-service correlation. */
public final class StructuredLogContext implements AutoCloseable {
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String USER_ID = "user_id";
    public static final String ORDER_ID = "order_id";
    public static final String EVENT_ID = "event_id";
    public static final String IDEMPOTENCY_KEY = "idempotency_key";
    public static final String OUTBOX_ID = "outbox_id";
    public static final String ERROR_CODE = "error_code";
    private final Map<String, String> previous;
    private StructuredLogContext(Map<String, String> previous) { this.previous = previous; }

    public static StructuredLogContext open(Map<String, ?> fields) {
        Map<String, String> prior = MDC.getCopyOfContextMap();
        if (fields != null) fields.forEach((key, value) -> { if (value != null) MDC.put(key, String.valueOf(value)); });
        return new StructuredLogContext(prior);
    }

    public static StructuredLogContext openMessage(MessageEnvelope message) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(TRACE_ID, message.traceId());
        fields.put(SPAN_ID, UUID.randomUUID().toString());
        fields.put(EVENT_ID, message.eventId());
        fields.put(IDEMPOTENCY_KEY, message.idempotencyKey());
        Object userId = message.payload().get(USER_ID);
        Object orderId = message.payload().get(ORDER_ID);
        fields.put(USER_ID, userId);
        fields.put(ORDER_ID, orderId);
        TraceContext.set(message.traceId());
        return open(fields);
    }

    public static Runnable wrap(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        String trace = TraceContext.get();
        return () -> { MDC.clear(); try (StructuredLogContext ignored = open(captured)) {
            if (trace != null) TraceContext.set(trace);
            task.run();
        } finally { TraceContext.clear(); } };
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        String trace = TraceContext.get();
        return () -> { MDC.clear(); try (StructuredLogContext ignored = open(captured)) {
            if (trace != null) TraceContext.set(trace);
            return task.call();
        } finally { TraceContext.clear(); } };
    }

    public static <T> Supplier<T> wrap(Supplier<T> task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        String trace = TraceContext.get();
        return () -> { MDC.clear(); try (StructuredLogContext ignored = open(captured)) {
            if (trace != null) TraceContext.set(trace);
            return task.get();
        } finally { TraceContext.clear(); } };
    }
    @Override public void close() {
        MDC.clear();
        if (previous != null) MDC.setContextMap(previous);
    }
}
