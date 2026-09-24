package com.flashsale.activity;

import java.util.Objects;
import org.springframework.stereotype.Service;

/** Fails closed until all barrier evidence agrees with the PostgreSQL projection. */
@Service
final class ActivityRecoveryVerifier {
    private final ActivityInventoryEventRepository events;
    private final ActivityInventoryReconciliationRepository reconciliation;

    ActivityRecoveryVerifier(ActivityInventoryEventRepository events, ActivityInventoryReconciliationRepository reconciliation) {
        this.events = Objects.requireNonNull(events);
        this.reconciliation = Objects.requireNonNull(reconciliation);
    }

    void verify(Activity activity, long barrier) {
        if (!events.unsentBefore(activity.id(), barrier).isEmpty()) throw new IllegalStateException("OUTBOX_NOT_SENT");
        if (events.checkpoint(activity.id()) < barrier) throw new IllegalStateException("CHECKPOINT_GAP");
        reconciliation.assertConsistent(activity.id(), barrier, activity.initialStock(), activity.availableStock());
    }
}
