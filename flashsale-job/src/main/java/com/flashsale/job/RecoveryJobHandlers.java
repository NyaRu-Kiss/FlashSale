package com.flashsale.job;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

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

    public ReconciliationTask.Report outboxRecovery(String traceId) { return reconciliation.run(rules, traceId); }
    public ReconciliationTask.Report consumerRecovery(String traceId) { return reconciliation.run(rules, traceId); }
    public ReconciliationTask.Report inventoryAndOrderReconciliation(String traceId) { return reconciliation.run(rules, traceId); }
}
