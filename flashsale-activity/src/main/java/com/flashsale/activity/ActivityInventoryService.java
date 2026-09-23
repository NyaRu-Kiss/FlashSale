package com.flashsale.activity;

import com.flashsale.common.trace.TraceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates Redis admission with durable, contiguous activity inventory events. */
@Service
final class ActivityInventoryService {
    private final ActivityRepository activities;
    private final ActivityInventoryPort inventory;
    private final ActivityInventoryEventRepository events;

    ActivityInventoryService(ActivityRepository activities, ActivityInventoryPort inventory,
                             ActivityInventoryEventRepository events) {
        this.activities = activities; this.inventory = inventory; this.events = events;
    }

    @Transactional
    ActivityInventoryLedger.Event reserve(long activityId, long userId, int quantity, String reservationKey,
                                          Long reservationId) {
        Activity activity = requireActive(activityId);
        ActivityInventoryPort.Reservation result = inventory.reserve(activity, userId, quantity, reservationKey);
        if (!result.accepted()) throw new IllegalArgumentException(result.reason());
        if ("DUPLICATE".equals(result.reason())) return null;
        return events.append(activityId, ActivityInventoryLedger.Kind.RESERVE, quantity, reservationId,
                "order", TraceContext.getOrCreate());
    }

    @Transactional
    ActivityInventoryLedger.Event release(long activityId, long userId, int quantity, String reservationKey,
                                          Long reservationId) {
        Activity activity = require(activityId);
        if (!inventory.release(activity, userId, quantity, reservationKey)) return null;
        return events.append(activityId, ActivityInventoryLedger.Kind.RELEASE, quantity, reservationId,
                "order", TraceContext.getOrCreate());
    }

    private Activity requireActive(long id) {
        Activity activity = require(id);
        if (activity.status() != ActivityStatus.ACTIVE) throw new IllegalArgumentException("ACTIVITY_NOT_ACTIVE");
        return activity;
    }
    private Activity require(long id) {
        Activity activity = activities.find(id);
        if (activity == null) throw new IllegalArgumentException("ACTIVITY_NOT_FOUND");
        return activity;
    }
}
