package com.flashsale.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Emits a structured warning until the production Alertmanager adapter is connected. */
@Component
final class LoggingActivityRecoveryAlert implements ActivityRecoveryAlert {
    private static final Logger log = LoggerFactory.getLogger(LoggingActivityRecoveryAlert.class);

    @Override
    public void recoveryFailed(long activityId, long recoveryJobId, String error) {
        log.error("activity recovery failed activity_id={} recovery_job_id={} error_code={}",
                activityId, recoveryJobId, error);
    }
}
