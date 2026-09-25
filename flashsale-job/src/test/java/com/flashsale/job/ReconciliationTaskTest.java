package com.flashsale.job;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ReconciliationTaskTest {
    @Test void successfulCompensationIsIdempotent() {
        var store = new InMemoryCompensationStore(); var calls = new AtomicInteger();
        var rule = new ReconciliationTask.ReconciliationRule() {
            public List<ReconciliationTask.Issue> findIssues() { return List.of(new ReconciliationTask.Issue("ORDER_1", "RELEASE_MISSING", "RESERVED", "RELEASED")); }
            public void repair(ReconciliationTask.Issue issue) { calls.incrementAndGet(); }
        };
        var task = new ReconciliationTask(store, Clock.systemUTC());
        assertEquals(new ReconciliationTask.Report(1, 1, 0), task.run(List.of(rule), "t1"));
        assertEquals(new ReconciliationTask.Report(1, 1, 0), task.run(List.of(rule), "t2"));
        assertEquals(1, calls.get());
    }
    @Test void failedCompensationIsRecordedForRetry() {
        var store = new InMemoryCompensationStore();
        var rule = new ReconciliationTask.ReconciliationRule() {
            public List<ReconciliationTask.Issue> findIssues() { return List.of(new ReconciliationTask.Issue("OUTBOX_1", "DELIVERY_STALE", "PENDING", "RETRY")); }
            public void repair(ReconciliationTask.Issue issue) { throw new IllegalStateException("broker down"); }
        };
        var report = new ReconciliationTask(store, Clock.systemUTC()).run(List.of(rule), "t1");
        assertEquals(new ReconciliationTask.Report(1, 0, 1), report);
        assertFalse(store.find("OUTBOX_1").orElseThrow().success());
    }

    @Test void failedCompensationRaisesManualAlert() {
        var store = new InMemoryCompensationStore();
        var alerts = new AtomicInteger();
        var rule = new ReconciliationTask.ReconciliationRule() {
            public List<ReconciliationTask.Issue> findIssues() { return List.of(new ReconciliationTask.Issue("ORDER_2", "MISSING", "A", "B")); }
            public void repair(ReconciliationTask.Issue issue) { throw new IllegalStateException("down"); }
        };
        new ReconciliationTask(store, record -> alerts.incrementAndGet(), Clock.systemUTC()).run(List.of(rule), "trace");
        assertEquals(1, alerts.get());
    }
}
