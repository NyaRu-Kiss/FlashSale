package com.flashsale.common.messaging;

import java.time.Duration;

public record OutboxBacklog(int pending, Duration oldestAge) {}
