package com.flashsale.common.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Low-cardinality metrics shared by Outbox, consumers and compensation jobs. */
public final class MessagingMetrics {
    private final AtomicInteger outboxPending = new AtomicInteger();
    private final AtomicInteger outboxOldestAgeSeconds = new AtomicInteger();
    private final AtomicInteger consumerProcessing = new AtomicInteger();
    private final AtomicInteger deadLetters = new AtomicInteger();
    private final Counter outboxSent;
    private final Counter outboxFailed;
    private final Counter outboxExhausted;
    private final Counter duplicateMessages;
    private final Counter compensationSucceeded;
    private final Counter compensationFailed;
    private final Timer consumerProcessingTimer;

    public MessagingMetrics(MeterRegistry registry) {
        outboxSent = registry.counter("flashsale_outbox_sent_total");
        outboxFailed = registry.counter("flashsale_outbox_failed_total");
        outboxExhausted = registry.counter("flashsale_outbox_exhausted_total");
        duplicateMessages = registry.counter("flashsale_consumer_duplicate_total");
        compensationSucceeded = registry.counter("flashsale_compensation_succeeded_total");
        compensationFailed = registry.counter("flashsale_compensation_failed_total");
        consumerProcessingTimer = registry.timer("flashsale_consumer_processing_duration");
        registry.gauge("flashsale_outbox_pending", outboxPending);
        registry.gauge("flashsale_outbox_oldest_age_seconds", outboxOldestAgeSeconds);
        registry.gauge("flashsale_consumer_processing", consumerProcessing);
        registry.gauge("flashsale_consumer_dead_letter", deadLetters);
    }
    public void outboxPending(int value, Duration oldestAge) { outboxPending.set(Math.max(0, value)); outboxOldestAgeSeconds.set((int)Math.max(0, oldestAge.toSeconds())); }
    public void outboxSent() { outboxSent.increment(); }
    public void outboxFailed() { outboxFailed.increment(); }
    public void outboxExhausted() { outboxExhausted.increment(); }
    public void consumerStarted() { consumerProcessing.incrementAndGet(); }
    public void consumerFinished(Duration duration) { consumerProcessing.updateAndGet(v -> Math.max(0, v - 1)); consumerProcessingTimer.record(duration); }
    public void duplicateMessage() { duplicateMessages.increment(); }
    public void deadLetter() { deadLetters.incrementAndGet(); }
    public void compensationSucceeded() { compensationSucceeded.increment(); }
    public void compensationFailed() { compensationFailed.increment(); }
}
