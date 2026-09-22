package com.flashsale.common.trace;

import org.slf4j.MDC;

import java.util.Map;

/** Scoped MDC fields for structured logs and cross-service correlation. */
public final class StructuredLogContext implements AutoCloseable {
    private final Map<String, String> previous;
    private StructuredLogContext(Map<String, String> previous) { this.previous = previous; }

    public static StructuredLogContext open(Map<String, ?> fields) {
        Map<String, String> prior = MDC.getCopyOfContextMap();
        if (fields != null) fields.forEach((key, value) -> { if (value != null) MDC.put(key, String.valueOf(value)); });
        return new StructuredLogContext(prior);
    }
    @Override public void close() {
        MDC.clear();
        if (previous != null) MDC.setContextMap(previous);
    }
}
