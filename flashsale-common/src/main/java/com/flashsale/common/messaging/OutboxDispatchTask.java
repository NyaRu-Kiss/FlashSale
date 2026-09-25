package com.flashsale.common.messaging;

import com.xxl.job.core.handler.annotation.XxlJob;
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
        var backlog = dispatcher.backlog();
        metrics.outboxPending(backlog.pending(), backlog.oldestAge());
        for (int i = 0; i < report.sent(); i++) metrics.outboxSent();
        for (int i = 0; i < report.failed(); i++) metrics.outboxFailed();
        for (int i = 0; i < report.deadLetterCandidates(); i++) metrics.outboxExhausted();
        if (report.deadLetterCandidates() > 0) LOG.error("Outbox exhausted retries: {}", report.deadLetterCandidates());
        return report;
    }

    @XxlJob("outboxDispatch")
    public void runXxlJob() {
        var report = dispatchOnce();
        if (report.failed() > 0 || report.deadLetterCandidates() > 0) {
            throw new IllegalStateException("OUTBOX_DISPATCH_RETRY_REQUIRED");
        }
    }
}
