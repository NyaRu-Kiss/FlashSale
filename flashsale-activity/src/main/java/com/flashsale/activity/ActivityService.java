package com.flashsale.activity;

import com.flashsale.common.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
final class ActivityService {
    private final ActivityRepository repository;
    private final ActivityStateMachine stateMachine = new ActivityStateMachine();

    ActivityService(ActivityRepository repository) { this.repository = repository; }

    @Transactional
    Activity create(Principal actor, Activity input) {
        requireOperator(actor);
        validate(input);
        return repository.create(input, actor.userId());
    }

    Activity getPublic(long id) {
        Activity activity = get(id);
        OffsetDateTime now = OffsetDateTime.now();
        if (activity.status() != ActivityStatus.ACTIVE || now.isBefore(activity.startsAt()) || !now.isBefore(activity.endsAt())) {
            throw new IllegalArgumentException("ACTIVITY_NOT_FOUND");
        }
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
        return changed(repository.casStatus(id, ActivityStatus.NOT_STARTED, ActivityStatus.CANCELLED, actor.userId()), "ACTIVITY_NOT_FOUND");
    }

    @Transactional
    Activity preheat(Principal actor, long id) {
        requireOperator(actor);
        Activity existing = get(id);
        if (existing.status() != ActivityStatus.NOT_STARTED) throw new IllegalArgumentException("INVALID_ACTIVITY_STATE");
        return existing;
    }

    @Transactional
    Activity start(Principal actor, long id) {
        requireOperator(actor);
        Activity existing = get(id);
        if (existing.status() != ActivityStatus.NOT_STARTED) throw new IllegalArgumentException("INVALID_ACTIVITY_STATE");
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(existing.startsAt()) || !now.isBefore(existing.endsAt())) throw new IllegalArgumentException("ACTIVITY_NOT_READY");
        return changed(repository.casStatusAt(id, ActivityStatus.NOT_STARTED, ActivityStatus.ACTIVE, actor.userId()), "ACTIVITY_NOT_READY");
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
