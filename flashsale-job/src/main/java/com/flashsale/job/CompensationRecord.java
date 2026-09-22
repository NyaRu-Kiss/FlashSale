package com.flashsale.job;

import java.time.Instant;

public record CompensationRecord(String key, String reason, String originalState, String targetState,
                                 String traceId, boolean success, String error, Instant completedAt) {}
