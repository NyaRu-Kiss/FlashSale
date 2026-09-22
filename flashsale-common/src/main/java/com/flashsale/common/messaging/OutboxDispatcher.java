package com.flashsale.common.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** At-least-once dispatcher: claiming and marking are deliberately separate from sending. */
public final class OutboxDispatcher {
    private final OutboxPort outbox;
    private final MessageTransport transport;
    private final OutboxStateMachine stateMachine;
    private final Clock clock;
    private final int batchSize;
    private final Duration lease;
    private final int maxAttempts;

    public OutboxDispatcher(OutboxPort outbox, MessageTransport transport,
                            OutboxStateMachine stateMachine, Clock clock,
                            int batchSize, Duration lease, int maxAttempts) {
        this.outbox = Objects.requireNonNull(outbox);
        this.transport = Objects.requireNonNull(transport);
        this.stateMachine = Objects.requireNonNull(stateMachine);
        this.clock = Objects.requireNonNull(clock);
        if (batchSize <= 0 || maxAttempts <= 0) throw new IllegalArgumentException("invalid dispatcher limits");
        this.batchSize = batchSize;
        this.lease = stateMachine.lease(lease);
        this.maxAttempts = maxAttempts;
    }

    public DispatchReport dispatchOnce() {
        Instant now = Instant.now(clock);
        List<OutboxRecord> records = outbox.claimDue(now, batchSize, lease);
        int sent = 0, failed = 0, dead = 0;
        for (OutboxRecord record : records) {
            try {
                transport.send(record.message());
                outbox.markSent(record.id(), Instant.now(clock));
                sent++;
            } catch (Exception error) {
                failed++;
                Instant next = stateMachine.nextAttempt(record, now);
                outbox.markFailed(record.id(), next, abbreviate(error));
                if (record.attemptCount() + 1 >= maxAttempts) dead++;
            }
        }
        return new DispatchReport(records.size(), sent, failed, dead);
    }

    private String abbreviate(Exception error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    public record DispatchReport(int claimed, int sent, int failed, int deadLetterCandidates) {}
}
