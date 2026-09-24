package com.flashsale.common.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic adapter used by unit tests and local failure simulations. */
public final class InMemoryOutbox implements OutboxPort {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, OutboxRecord> records = new ConcurrentHashMap<>();

    public synchronized OutboxRecord add(MessageEnvelope message, Instant availableAt) {
        var record = new OutboxRecord(ids.getAndIncrement(), message.eventId(), message.idempotencyKey(),
                message, OutboxStatus.PENDING, 0, availableAt, null);
        records.put(record.id(), record);
        return record;
    }

    @Override public synchronized List<OutboxRecord> claimDue(Instant now, int batchSize, Duration lease) {
        return records.values().stream().filter(r -> r.dispatchable(now)).sorted(Comparator.comparingLong(OutboxRecord::id))
                .limit(batchSize).map(r -> {
                    var claimed = new OutboxRecord(r.id(), r.eventId(), r.idempotencyKey(), r.message(), r.status(),
                            r.attemptCount() + 1, r.availableAt(), now.plus(lease));
                    records.put(r.id(), claimed); return claimed;
                }).toList();
    }
    @Override public synchronized boolean markSent(OutboxRecord record, Instant sentAt) {
        var r = require(record.id());
        if (!Objects.equals(r.lockedUntil(), record.lockedUntil()) || r.status() == OutboxStatus.SENT) return false;
        records.put(record.id(), new OutboxRecord(r.id(), r.eventId(), r.idempotencyKey(), r.message(),
                OutboxStatus.SENT, r.attemptCount(), r.availableAt(), null));
        return true;
    }
    @Override public synchronized boolean markFailed(OutboxRecord record, Instant nextAttemptAt, String error) {
        var r = require(record.id());
        if (!Objects.equals(r.lockedUntil(), record.lockedUntil()) || r.status() == OutboxStatus.SENT) return false;
        records.put(record.id(), new OutboxRecord(r.id(), r.eventId(), r.idempotencyKey(), r.message(),
                OutboxStatus.FAILED, r.attemptCount(), nextAttemptAt, null));
        return true;
    }
    public Optional<OutboxRecord> find(long id) { return Optional.ofNullable(records.get(id)); }
    @Override public OutboxBacklog backlog(Instant now) {
        int pending = (int) records.values().stream().filter(r -> r.status() != OutboxStatus.SENT).count();
        return new OutboxBacklog(pending, Duration.ZERO);
    }
    private OutboxRecord require(long id) { return Optional.ofNullable(records.get(id)).orElseThrow(); }
}
