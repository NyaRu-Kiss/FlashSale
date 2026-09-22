package com.flashsale.order;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderPreviewServiceTest {
    private final ProductGateway products = id -> new ProductGateway.ProductSnapshot(id, "SKU-"+id,
            "P"+id, 1000, 800, true);
    private final ActivityGateway activities = id -> new ActivityGateway.ActivitySnapshot(id, 1, 500,
            Instant.parse("2026-01-01T00:00:00Z").atOffset(ZoneOffset.UTC),
            Instant.parse("2027-01-01T00:00:00Z").atOffset(ZoneOffset.UTC), "ACTIVE", 1);
    private final CouponGateway coupons = (u, c) -> new CouponGateway.CouponSnapshot(c, u, 1000, 100, true);

    @Test void calculatesSnapshotWithoutReservation() {
        var s = new OrderPreviewService(products, activities, coupons,
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
        var p = s.preview(new OrderPreviewRequest(7, OrderKind.DIRECT, null, 9L,
                List.of(new OrderPreviewRequest.Item(1, 2), new OrderPreviewRequest.Item(2, 1))));
        assertEquals(3000, p.itemSubtotalMinor());
        assertEquals(0, p.activityDiscountMinor());
        assertEquals(100, p.couponDiscountMinor());
        assertEquals(2900, p.payableAmountMinor());
    }

    @Test void activityRequiresSingleMatchingProductAndWindow() {
        var s = new OrderPreviewService(products, activities, coupons,
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> s.preview(new OrderPreviewRequest(1,
                OrderKind.ACTIVITY, 2L, null, List.of(new OrderPreviewRequest.Item(2, 1)))));
    }
}
