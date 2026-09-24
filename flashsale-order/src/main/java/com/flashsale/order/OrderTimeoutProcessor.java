package com.flashsale.order;
import java.time.*; import java.util.*;
public final class OrderTimeoutProcessor {
 private final OrderCancellationService cancellation;
 public OrderTimeoutProcessor(OrderCancellationService cancellation){this.cancellation=cancellation;}
 public Order handle(String orderNumber){return cancellation.cancelTimeout(orderNumber);}
 public int scan(Collection<String> orderNumbers){int n=0;for(String number:orderNumbers){handle(number);n++;}return n;}
}
