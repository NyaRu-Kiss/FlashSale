package com.flashsale.activity;

import com.flashsale.common.security.Principal;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
final class ActivityService {
    private final ActivityRepository repository;
    private final ActivityInventoryPort inventory;
    private final ActivityRecoveryRepository recoveries;
    private final ActivityInventoryEventRepository events;
    private final ActivityStateMachine stateMachine = new ActivityStateMachine();
    @Value("${flashsale.activity.preheat-window:PT10M}")
    private Duration preheatWindow = Duration.ofMinutes(10);

    ActivityService(ActivityRepository repository, ActivityInventoryPort inventory, ActivityRecoveryRepository recoveries, ActivityInventoryEventRepository events) { this.repository = repository; this.inventory = inventory; this.recoveries = recoveries; this.events = events; }

    @Transactional
    Activity create(Principal actor, Activity input) {
        requireOperator(actor);
        validate(input);
        return repository.create(input, actor.userId());
    }

    Activity getPublic(long id) {
        Activity activity = repository.findPublic(id);
        if (activity == null) throw new IllegalArgumentException("ACTIVITY_NOT_FOUND");
        return activity;
    }

    List<Activity> listPublic() { return repository.listPublic(); }
    long countPublic() { return repository.countPublic(); }

    List<Activity> listOperator(Principal actor) { requireOperator(actor); return repository.listAll(); }
    long countOperator(Principal actor) { requireOperator(actor); return repository.countAll(); }
    Activity getOperator(Principal actor, long id) { requireOperator(actor); return get(id); }

    @Transactional
    Activity cancel(Principal actor, long id) {
        requireOperator(actor);
        Activity existing = get(id);
        if (existing.status() != ActivityStatus.NOT_STARTED) throw new IllegalArgumentException("INVALID_ACTIVITY_STATE");
        Activity cancelled = changed(repository.casStatus(id, ActivityStatus.NOT_STARTED, ActivityStatus.CANCELLED, actor.userId()), "ACTIVITY_NOT_FOUND");
        repository.audit(actor.userId(), cancelled.id(), "CANCEL", existing, cancelled);
        return cancelled;
    }

    @Transactional
    Activity preheat(Principal actor, long id) {
        requireOperator(actor);
        return preheat(id);
    }

    @Transactional
    Activity preheat(long id) {
        Activity existing = repository.findPreheatCandidate(id, preheatWindow);
        if (existing == null) throw new IllegalArgumentException("ACTIVITY_NOT_IN_PREHEAT_WINDOW");
        inventory.preheat(existing);
        repository.recordPreheatReady(existing, com.flashsale.common.trace.TraceContext.getOrCreate());
        return existing;
    }

    @Transactional
    int preheatDueActivities() {
        int count = 0;
        for (Activity activity : repository.findPreheatCandidates(preheatWindow)) {
            inventory.preheat(activity);
            repository.recordPreheatReady(activity, com.flashsale.common.trace.TraceContext.getOrCreate());
            count++;
        }
        return count;
    }

    @Transactional
    Activity start(Principal actor, long id) {
        requireOperator(actor);
        Activity existing = get(id);
        if (existing.status() != ActivityStatus.NOT_STARTED) throw new IllegalArgumentException("INVALID_ACTIVITY_STATE");
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(existing.startsAt()) || !now.isBefore(existing.endsAt())) throw new IllegalArgumentException("ACTIVITY_NOT_READY");
        if (!inventory.hasPreheatedKeys(id)) throw new IllegalArgumentException("ACTIVITY_NOT_READY");
        Activity started = changed(repository.casStatusAt(id, ActivityStatus.NOT_STARTED, ActivityStatus.ACTIVE, actor.userId()), "ACTIVITY_NOT_READY");
        if (!inventory.activate(started)) throw new IllegalStateException("ACTIVITY_PREHEAT_MISSING");
        repository.audit(actor.userId(), started.id(), "RESUME", existing, started);
        return started;
    }

    @Transactional
    Activity pause(Principal actor, long id) {
        requireOperator(actor);
        Activity existing = get(id);
        if (existing.status() != ActivityStatus.ACTIVE) throw new IllegalArgumentException("INVALID_ACTIVITY_STATE");
        // The Redis Lua gate closes before the drain; the sequence lock is deliberately taken afterwards.
        inventory.closeGateAndAwaitInFlight(id);
        long barrier = repository.lockAndReadBarrier(id);
        Activity paused = changed(repository.pauseWithBarrier(id, barrier, actor.userId()), "INVALID_ACTIVITY_STATE");
        repository.audit(actor.userId(), paused.id(), "PAUSE", existing, paused);
        return paused;
    }

    @Transactional
    Activity end(Principal actor, long id) {
        requireOperator(actor); Activity existing = get(id);
        Activity ended = repository.endIfDue(id, actor.userId());
        if (ended == null) throw new IllegalArgumentException("ACTIVITY_NOT_ACTIVE");
        inventory.closeGate(id);
        repository.audit(actor.userId(), ended.id(), "UPDATE", existing, ended);
        return ended;
    }

    @Transactional
    ActivityRecoveryJob resume(Principal actor, long id) {
        requireOperator(actor);
        if (get(id).status() != ActivityStatus.PAUSED) throw new IllegalArgumentException("INVALID_ACTIVITY_STATE");
        return recoveries.pendingOrCreate(id, actor.userId());
    }

    ActivityRecoveryJob recovery(Principal actor, long id) { requireOperator(actor); get(id); return recoveries.latest(id); }

    ActivityMetrics metrics(Principal actor, long id) {
        requireOperator(actor); Activity activity = get(id); long checkpoint = events.checkpoint(id); long last = events.lastSequence(id);
        return new ActivityMetrics(activity.status(), activity.availableStock(), last, checkpoint,
                last, activity.status() == ActivityStatus.PAUSED && checkpoint >= last);
    }

    private Activity get(long id) {
        Activity activity = repository.find(id);
        if (activity == null) throw new IllegalArgumentException("ACTIVITY_NOT_FOUND");
        return activity;
    }

    private static Activity changed(Activity value, String error) { if (value == null) throw new IllegalArgumentException(error); return value; }
    private static void requireOperator(Principal actor) { if (actor == null || !actor.canManageBusiness()) throw new IllegalArgumentException("FORBIDDEN"); }
    private static void validate(Activity x) {
        if (x == null || x.name() == null || x.name().isBlank() || x.productId() <= 0 || x.salePriceMinor() < 0
                || x.initialStock() <= 0 || x.purchaseLimitPerUser() <= 0 || x.startsAt() == null || x.endsAt() == null
                || !x.startsAt().isBefore(x.endsAt())) throw new IllegalArgumentException("VALIDATION_ERROR");
    }
}
