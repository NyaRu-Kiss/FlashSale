package com.flashsale.order;

import java.util.function.Supplier;

public interface OrderCreationStore {
    Order create(OrderCreateRequest request, String fingerprint, Supplier<OrderPreview> preview);
}
