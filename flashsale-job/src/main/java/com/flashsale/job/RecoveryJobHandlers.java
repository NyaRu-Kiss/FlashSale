package com.flashsale.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import com.flashsale.common.trace.StructuredLogContext;
import com.flashsale.common.trace.TraceContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Named entry points for XXL-Job registration; deployment may bind each method to a schedule. */
@Component
public final class RecoveryJobHandlers {
    private final ReconciliationTask reconciliation;
    private final List<ReconciliationTask.ReconciliationRule> rules;

    public RecoveryJobHandlers(ReconciliationTask reconciliation,
                               List<ReconciliationTask.ReconciliationRule> rules) {
        this.reconciliation = Objects.requireNonNull(reconciliation);
        this.rules = List.copyOf(rules);
    }

    @XxlJob("outboxRecovery")
    public ReconciliationTask.Report outboxRecovery(String traceId) { return runAndRetry(traceId); }
    @XxlJob("consumerRecovery")
    public ReconciliationTask.Report consumerRecovery(String traceId) { return runAndRetry(traceId); }
    @XxlJob("inventoryAndOrderReconciliation")
    public ReconciliationTask.Report inventoryAndOrderReconciliation(String traceId) { return runAndRetry(traceId); }

    private ReconciliationTask.Report runAndRetry(String traceId) {
        try (StructuredLogContext ignored = StructuredLogContext.open(java.util.Map.of(
                StructuredLogContext.TRACE_ID, traceId == null || traceId.isBlank() ? TraceContext.getOrCreate() : traceId,
                StructuredLogContext.SPAN_ID, java.util.UUID.randomUUID()))) {
        var report = reconciliation.run(rules, traceId);
        if (report.failed() > 0) throw new IllegalStateException("COMPENSATION_RETRY_REQUIRED");
        return report;
        }
    }
}
