package com.flashsale.activity;

import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.flashsale.common.trace.TraceContext;

/** Recovery remains PAUSED unless all barrier evidence has been verified. */
@Component
final class ActivityRecoveryWorker {
    private final ActivityRepository activities;
    private final ActivityRecoveryRepository jobs;
    private final ActivityInventoryPort inventory;
    private final ActivityRecoveryVerifier verifier;
    private final ActivityRecoveryAlert alerts;

    ActivityRecoveryWorker(ActivityRepository activities, ActivityRecoveryRepository jobs, ActivityInventoryPort inventory,
                           ActivityRecoveryVerifier verifier) {
        this(activities, jobs, inventory, verifier, (activityId, recoveryJobId, error) -> { });
    }

    ActivityRecoveryWorker(ActivityRepository activities, ActivityRecoveryRepository jobs, ActivityInventoryPort inventory,
                           ActivityRecoveryVerifier verifier, ActivityRecoveryAlert alerts) {
        this.activities = activities;
        this.jobs = jobs;
        this.inventory = inventory;
        this.verifier = verifier;
        this.alerts = alerts;
    }

    @XxlJob("activityRecovery")
    public void executeDueJobs() {
        boolean failed = jobs.pendingIds().stream().map(this::run).anyMatch(done -> !done);
        if (failed) throw new IllegalStateException("ACTIVITY_RECOVERY_RETRY_REQUIRED");
    }

    @Transactional
    boolean run(long id) {
        ActivityRecoveryJob initial = jobs.byId(id);
        if (initial == null) return true;
        long barrier = activities.lockAndReadBarrier(initial.activityId());
        ActivityRecoveryJob job = jobs.claim(id, barrier);
        if (job == null) return true;
        try {
            Activity activity = activities.find(job.activityId());
            if (activity == null || activity.status() != ActivityStatus.PAUSED) throw new IllegalStateException("ACTIVITY_NOT_PAUSED");
            verifier.verify(activity, barrier);
            OffsetDateTime now = OffsetDateTime.now();
            if (now.isBefore(activity.startsAt()) || !now.isBefore(activity.endsAt())) throw new IllegalStateException("ACTIVITY_NOT_READY");
            Activity projected = new Activity(activity.id(), activity.name(), activity.productId(), activity.salePriceMinor(),
                    activity.initialStock(), activity.availableStock(), activity.purchaseLimitPerUser(), activity.startsAt(),
                    activity.endsAt(), ActivityStatus.ACTIVE, false, activity.updatedBy());
            inventory.ensureRecoveryProjection(projected, projected.availableStock());
            Activity active = activities.casStatus(activity.id(), ActivityStatus.PAUSED, ActivityStatus.ACTIVE, job.requestedBy());
            if (active == null) throw new IllegalStateException("ACTIVITY_NOT_PAUSED");
            activities.audit(job.requestedBy(), active.id(), "RESUME", activity, active);
            jobs.succeeded(id, activity.id(), TraceContext.getOrCreate());
            return true;
        } catch (RuntimeException error) {
            inventory.closeGate(initial.activityId());
            jobs.failed(id, error.getMessage());
            alerts.recoveryFailed(initial.activityId(), id, error.getMessage());
            return false;
        }
    }
}
