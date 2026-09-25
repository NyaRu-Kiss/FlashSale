package com.flashsale.common.metrics;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusAlertRulesTest {
    @Test void rulesCoverBusinessFailureAndResourceSignals() throws Exception {
        String rules = Files.readString(Path.of("../config/prometheus/alerts.yml"));
        for (String metric : new String[]{"flashsale_request_error_total", "flashsale_request_duration_seconds_bucket",
                "flashsale_reserve_total", "flashsale_coupon_claim_total", "flashsale_order_total",
                "flashsale_payment_total", "flashsale_idempotency_conflict_total", "hikaricp_connections_pending",
                "flashsale_outbox_pending", "flashsale_consumer_dead_letter", "flashsale_compensation_failed_total"}) {
            assertTrue(rules.contains(metric), () -> "missing alert metric: " + metric);
        }
    }
}
