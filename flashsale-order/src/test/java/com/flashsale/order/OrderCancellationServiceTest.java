package com.flashsale.order;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderCancellationServiceTest {
    @Test void cancellationDelegatesOneDurableCasAndRepeatedCallReadsFinalState() {
        AtomicInteger calls = new AtomicInteger();
        Order cancelled = new Order("o", 1, OrderKind.DIRECT, null, 1, 0, 0, 1, "CNY",
                Order.Status.CANCELLED, OffsetDateTime.parse("2026-01-01T00:15:00Z"), java.util.List.of());
        OrderCancellationStore store = new OrderCancellationStore() {
            @Override public Order cancel(long user, String number, String reason, OffsetDateTime now) {
                calls.incrementAndGet();
                assertEquals("USER_CANCEL", reason);
                return cancelled;
            }
            @Override public Order cancelTimeout(String number, OffsetDateTime now) { return cancelled; }
        };
        var service = new OrderCancellationService(store, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertEquals(Order.Status.CANCELLED, service.cancel(1, "o", "USER_CANCEL").status());
        assertEquals(Order.Status.CANCELLED, service.cancel(1, "o", "USER_CANCEL").status());
        assertEquals(2, calls.get());
    }
}
