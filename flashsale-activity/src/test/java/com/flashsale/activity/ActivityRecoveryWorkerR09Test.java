package com.flashsale.activity;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityRecoveryWorkerR09Test {
    @Mock ActivityRepository activities;
    @Mock ActivityRecoveryRepository jobs;
    @Mock ActivityInventoryPort inventory;
    @Mock ActivityRecoveryVerifier verifier;

    @Test void reconciliationFailureKeepsRedisStockUntouchedAndRecoveryFailed() {
        ActivityRecoveryJob job = job();
        Activity paused = paused();
        when(jobs.byId(5)).thenReturn(job);
        when(activities.lockAndReadBarrier(11)).thenReturn(4L);
        when(jobs.claim(5, 4)).thenReturn(job);
        when(activities.find(11)).thenReturn(paused);
        doThrow(new IllegalStateException("ACTIVITY_LEDGER_MISMATCH")).when(verifier).verify(paused, 4L);
        ActivityRecoveryWorker worker = new ActivityRecoveryWorker(activities, jobs, inventory, verifier);

        worker.run(5);

        verify(inventory, never()).ensureRecoveryProjection(any(), anyInt());
        verify(inventory).closeGate(11);
        verify(jobs).failed(5, "ACTIVITY_LEDGER_MISMATCH");
        verify(activities, never()).casStatus(anyLong(), any(), any(), anyLong());
    }

    @Test void consistentRecoveryOnlyRebuildsMissingStockAtomically() {
        ActivityRecoveryJob job = job();
        Activity paused = paused();
        when(jobs.byId(5)).thenReturn(job);
        when(activities.lockAndReadBarrier(11)).thenReturn(4L);
        when(jobs.claim(5, 4)).thenReturn(job);
        when(activities.find(11)).thenReturn(paused);
        Activity active = active();
        when(activities.casStatus(11, ActivityStatus.PAUSED, ActivityStatus.ACTIVE, 7)).thenReturn(active);
        ActivityRecoveryWorker worker = new ActivityRecoveryWorker(activities, jobs, inventory, verifier);

        worker.run(5);

        verify(inventory).ensureRecoveryProjection(any(Activity.class), eq(20));
        verify(inventory, never()).rebuild(any(), anyInt());
    }

    private static ActivityRecoveryJob job() {
        return new ActivityRecoveryJob(5, 11, 7, 4L, ActivityRecoveryStatus.RUNNING, null,
                OffsetDateTime.now(), null);
    }
    private static Activity paused() { return activity(ActivityStatus.PAUSED); }
    private static Activity active() { return activity(ActivityStatus.ACTIVE); }
    private static Activity activity(ActivityStatus status) {
        OffsetDateTime start = OffsetDateTime.now().minusMinutes(10);
        return new Activity(11, "sale", 3, 100, 20, 20, 2, start, start.plusHours(1), status, false, 7);
    }
}
