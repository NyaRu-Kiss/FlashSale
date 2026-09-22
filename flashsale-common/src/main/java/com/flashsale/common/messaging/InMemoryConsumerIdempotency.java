package com.flashsale.common.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryConsumerIdempotency implements ConsumerIdempotencyPort {
    private final Map<String, ConsumerIdempotencyRecord> records = new ConcurrentHashMap<>();

    @Override public synchronized boolean tryStart(String key, String eventId, Instant now, Duration timeout) {
        var prior = records.get(key);
        if (prior != null && prior.status() == ConsumerStatus.SUCCEEDED) return false;
        if (prior != null && prior.status() == ConsumerStatus.PROCESSING && !prior.processingExpired(now, timeout)) return false;
        records.put(key, new ConsumerIdempotencyRecord(key, eventId, ConsumerStatus.PROCESSING,
                prior == null ? now : prior.startedAt(), now, null));
        return true;
    }
    @Override public synchronized void markSucceeded(String key, Instant now) {
        var prior = require(key); records.put(key, new ConsumerIdempotencyRecord(key, prior.eventId(), ConsumerStatus.SUCCEEDED,
                prior.startedAt(), now, null));
    }
    @Override public synchronized void markFailed(String key, Instant now, String error) {
        var prior = require(key); records.put(key, new ConsumerIdempotencyRecord(key, prior.eventId(), ConsumerStatus.FAILED,
                prior.startedAt(), now, error));
    }
    public ConsumerIdempotencyRecord find(String key) { return records.get(key); }
    private ConsumerIdempotencyRecord require(String key) { var r = records.get(key); if (r == null) throw new IllegalStateException("CONSUMER_RECORD_NOT_FOUND"); return r; }
}
