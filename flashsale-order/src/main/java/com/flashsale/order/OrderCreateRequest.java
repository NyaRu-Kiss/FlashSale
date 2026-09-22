package com.flashsale.order;

import java.util.List;

public record OrderCreateRequest(long userId, String idempotencyKey, OrderPreviewRequest preview) {
    public List<OrderPreviewRequest.Item> items() { return preview.items(); }
}
