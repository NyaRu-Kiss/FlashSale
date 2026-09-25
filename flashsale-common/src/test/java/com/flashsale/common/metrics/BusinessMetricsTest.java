package com.flashsale.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessMetricsTest {
    @Test void exposesStableLowCardinalityBusinessMetricNamesAndTags() {
        var registry = new SimpleMeterRegistry();
        var metrics = new BusinessMetrics(registry);
        metrics.request("order", "create", "SUCCESS", Duration.ofMillis(12));
        metrics.request("order", "create", "ERROR_TIMEOUT", Duration.ofMillis(20));
        metrics.reserve("activity", "SUCCESS");
        metrics.couponClaim("CONFLICT");
        metrics.order("create", "SUCCESS");
        metrics.payment("callback", "SUCCESS");
        metrics.idempotencyConflict("coupon");

        assertEquals(1, registry.get("flashsale_request_total").tag("service", "order").tag("result", "SUCCESS").counter().count());
        assertEquals(1, registry.get("flashsale_request_error_total").tag("operation", "create").counter().count());
        assertEquals(1, registry.get("flashsale_reserve_total").tag("result", "SUCCESS").counter().count());
        assertEquals(1, registry.get("flashsale_coupon_claim_total").counter().count());
        assertEquals(1, registry.get("flashsale_idempotency_conflict_total").counter().count());
    }
}
