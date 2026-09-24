package com.flashsale.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One bounded delivery pass; scheduling is supplied by XXL-Job in R20. */
public final class OutboxDispatchTask {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatchTask.class);
    private final OutboxDispatcher dispatcher;
    private final MessagingMetrics metrics;

    public OutboxDispatchTask(OutboxDispatcher dispatcher, MessagingMetrics metrics) {
        this.dispatcher = dispatcher;
        this.metrics = metrics;
    }

    public OutboxDispatcher.DispatchReport dispatchOnce() {
        var report = dispatcher.dispatchOnce();
        for (int i = 0; i < report.sent(); i++) metrics.outboxSent();
        for (int i = 0; i < report.failed(); i++) metrics.outboxFailed();
        for (int i = 0; i < report.deadLetterCandidates(); i++) metrics.deadLetter();
        if (report.deadLetterCandidates() > 0) LOG.error("Outbox exhausted retries: {}", report.deadLetterCandidates());
        return report;
    }
}
