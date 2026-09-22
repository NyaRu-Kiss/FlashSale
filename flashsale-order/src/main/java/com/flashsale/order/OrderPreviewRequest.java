package com.flashsale.order;

import java.util.List;

public record OrderPreviewRequest(long userId, OrderKind kind, Long activityId, Long couponId,
                                  List<Item> items) {
    public record Item(long productId, int quantity) {}
}
