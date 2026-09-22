package com.flashsale.common.messaging;

import java.time.Duration;

public final class BackoffPolicy {
    private final Duration initial;
    private final Duration maximum;

    public BackoffPolicy(Duration initial, Duration maximum) {
        if (initial.isNegative() || initial.isZero() || maximum.compareTo(initial) < 0) {
            throw new IllegalArgumentException("invalid backoff policy");
        }
        this.initial = initial;
        this.maximum = maximum;
    }

    public Duration delay(int attemptCount) {
        if (attemptCount < 0) throw new IllegalArgumentException("attempt count must be non-negative");
        long multiplier = 1L << Math.min(attemptCount, 30);
        Duration candidate;
        try {
            candidate = initial.multipliedBy(multiplier);
        } catch (ArithmeticException overflow) {
            candidate = maximum;
        }
        return candidate.compareTo(maximum) > 0 ? maximum : candidate;
    }
}
