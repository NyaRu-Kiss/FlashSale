package com.flashsale.activity;

import org.springframework.stereotype.Component;

/** Callable task entrypoint for an external scheduler; intentionally not scheduled locally. */
@Component
public final class ActivityPreheatTask {
    private final ActivityService service;

    ActivityPreheatTask(ActivityService service) { this.service = service; }

    public int run() { return service.preheatDueActivities(); }
}
