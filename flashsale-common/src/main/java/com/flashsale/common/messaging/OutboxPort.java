package com.flashsale.common.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Port implemented by each service's local Outbox repository. */
public interface OutboxPort {
    List<OutboxRecord> claimDue(Instant now, int batchSize, Duration lease);
    void markSent(long id, Instant sentAt);
    void markFailed(long id, Instant nextAttemptAt, String error);
}
