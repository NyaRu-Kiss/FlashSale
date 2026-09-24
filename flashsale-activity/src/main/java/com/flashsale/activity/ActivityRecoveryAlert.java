package com.flashsale.activity;

/** Alert boundary for recovery failures; the delivery implementation is replaceable by the monitoring stack. */
@FunctionalInterface
interface ActivityRecoveryAlert {
    void recoveryFailed(long activityId, long recoveryJobId, String error);
}
