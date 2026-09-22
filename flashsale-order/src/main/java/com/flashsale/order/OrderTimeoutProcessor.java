package com.flashsale.order;
import java.time.*; import java.util.*;
public final class OrderTimeoutProcessor {
 private final OrderRepository orders; private final OrderCancellationService cancellation; private final Clock clock;
 public OrderTimeoutProcessor(OrderRepository orders,OrderCancellationService cancellation,Clock clock){this.orders=orders;this.cancellation=cancellation;this.clock=clock;}
 public Order handle(String orderNumber){var o=orders.findByNumber(orderNumber).orElseThrow();if(o.status()!=Order.Status.PENDING_PAYMENT)return o;if(o.expiresAt().isAfter(OffsetDateTime.now(clock)))return o;return cancellation.cancel(o.userId(),orderNumber,"PAYMENT_TIMEOUT");}
 public int scan(Collection<String> orderNumbers){int n=0;for(String number:orderNumbers){var before=orders.findByNumber(number).orElse(null);if(before!=null&&before.status()==Order.Status.PENDING_PAYMENT&&!before.expiresAt().isAfter(OffsetDateTime.now(clock))){handle(number);n++;}}return n;}
}
