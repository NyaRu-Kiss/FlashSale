package com.flashsale.order;

import java.time.OffsetDateTime;

public interface OrderCancellationStore {
    Order cancel(long userId, String orderNumber, String reason, OffsetDateTime now);
    Order cancelTimeout(String orderNumber, OffsetDateTime now);
}
