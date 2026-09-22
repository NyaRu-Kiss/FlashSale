package com.flashsale.job;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Generic idempotent reconciliation runner used by XXL-Job handlers. */
public final class ReconciliationTask {
    private final CompensationStore compensationStore;
    private final Clock clock;

    public ReconciliationTask(CompensationStore compensationStore, Clock clock) {
        this.compensationStore = Objects.requireNonNull(compensationStore);
        this.clock = Objects.requireNonNull(clock);
    }

    public Report run(List<ReconciliationRule> rules, String traceId) {
        int checked = 0, repaired = 0, failed = 0;
        for (var rule : rules) {
            for (var issue : rule.findIssues()) {
                checked++;
                var existing = compensationStore.find(issue.key());
                if (existing.isPresent() && existing.get().success()) { repaired++; continue; }
                try {
                    rule.repair(issue);
                    compensationStore.save(new CompensationRecord(issue.key(), issue.reason(), issue.originalState(),
                            issue.targetState(), traceId, true, null, Instant.now(clock)));
                    repaired++;
                } catch (Exception error) {
                    compensationStore.save(new CompensationRecord(issue.key(), issue.reason(), issue.originalState(),
                            issue.targetState(), traceId, false, error.toString(), Instant.now(clock)));
                    failed++;
                }
            }
        }
        return new Report(checked, repaired, failed);
    }

    public record Report(int checked, int repaired, int failed) {}
    public record Issue(String key, String reason, String originalState, String targetState) {}
    public interface ReconciliationRule {
        List<Issue> findIssues();
        void repair(Issue issue) throws Exception;
    }
}
