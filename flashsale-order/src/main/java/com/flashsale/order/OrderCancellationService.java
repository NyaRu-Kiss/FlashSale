package com.flashsale.order;

import java.time.Clock;
import java.time.OffsetDateTime;

public final class OrderCancellationService {
    private final OrderCancellationStore store;
    private final Clock clock;
    public OrderCancellationService(OrderCancellationStore store, Clock clock){this.store=store;this.clock=clock;}
    public Order cancel(long userId,String orderNumber,String reason){
        return store.cancel(userId,orderNumber,reason,OffsetDateTime.now(clock));
    }
    public Order cancelIfExpired(long userId,String orderNumber){return cancel(userId,orderNumber,"PAYMENT_TIMEOUT");}
    public Order cancelTimeout(String orderNumber){return store.cancelTimeout(orderNumber,OffsetDateTime.now(clock));}
}
