package com.flashsale.activity;
import java.time.OffsetDateTime;
record ActivityRecoveryJob(long id, long activityId, long requestedBy, Long barrier, ActivityRecoveryStatus status, String lastError, OffsetDateTime requestedAt, OffsetDateTime completedAt) {}
