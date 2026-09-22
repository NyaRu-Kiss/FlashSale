package com.flashsale.order;

import java.util.List;

public record OrderPreview(OrderKind kind, long itemSubtotalMinor, long activityDiscountMinor,
                           long couponDiscountMinor, long payableAmountMinor,
                           List<Item> items) {
    public record Item(long productId, int quantity, String sku, String productName,
                       long listPriceMinor, long salePriceMinor, long activityDiscountMinor,
                       long lineAmountMinor) {}
}
