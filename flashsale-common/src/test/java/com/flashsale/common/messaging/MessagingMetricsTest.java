package com.flashsale.common.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class MessagingMetricsTest {
    @Test void recordsCoreCountersAndGauges() {
        var registry = new SimpleMeterRegistry(); var metrics = new MessagingMetrics(registry);
        metrics.outboxPending(3, Duration.ofSeconds(8)); metrics.outboxSent(); metrics.outboxFailed(); metrics.duplicateMessage(); metrics.deadLetter(); metrics.compensationSucceeded(); metrics.compensationFailed();
        assertEquals(3, registry.get("flashsale_outbox_pending").gauge().value());
        assertEquals(8, registry.get("flashsale_outbox_oldest_age_seconds").gauge().value());
        assertEquals(1, registry.get("flashsale_outbox_sent_total").counter().count());
        assertEquals(1, registry.get("flashsale_compensation_failed_total").counter().count());
    }
}
