package com.flashsale.order;

import java.time.OffsetDateTime;
import java.util.List;

public record Order(String orderNumber, long userId, OrderKind kind, Long activityId,
                    long itemSubtotalMinor, long activityDiscountMinor, long couponDiscountMinor,
                    long payableAmountMinor, String currency, Status status,
                    OffsetDateTime expiresAt, List<Item> items) {
    public enum Status { PENDING_PAYMENT, PAID, COMPLETED, CANCELLED }
    public record Item(long productId, int quantity, String sku, String productName,
                       long listPriceMinor, long salePriceMinor, long activityDiscountMinor,
                       long lineAmountMinor, String reservationKey) {}
}
