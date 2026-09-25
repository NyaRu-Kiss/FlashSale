package com.flashsale.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;

/** Low-cardinality business metrics shared by all services. */
public final class BusinessMetrics {
    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public void request(String service, String operation, String result, Duration duration) {
        tags("flashsale_request_total", "service", service, "operation", operation, "result", result).increment();
        Timer.builder("flashsale_request_duration_seconds").tags("service", service, "operation", operation)
                .publishPercentileHistogram().register(registry).record(duration);
        if (result.startsWith("ERROR")) tags("flashsale_request_error_total", "service", service, "operation", operation, "result", result).increment();
    }

    public void reserve(String service, String result) { tags("flashsale_reserve_total", "service", service, "result", result).increment(); }
    public void couponClaim(String result) { tags("flashsale_coupon_claim_total", "result", result).increment(); }
    public void order(String operation, String result) { tags("flashsale_order_total", "operation", operation, "result", result).increment(); }
    public void payment(String operation, String result) { tags("flashsale_payment_total", "operation", operation, "result", result).increment(); }
    public void idempotencyConflict(String domain) { tags("flashsale_idempotency_conflict_total", "domain", domain).increment(); }

    private Counter tags(String name, String... tags) { return Counter.builder(name).tags(tags).register(registry); }
}
