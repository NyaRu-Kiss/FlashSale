package com.flashsale.order;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

public final class OrderCancellationService {
    private final OrderRepository orders; private final InventoryGateway inventory; private final Clock clock;
    public OrderCancellationService(OrderRepository orders, InventoryGateway inventory, Clock clock){this.orders=Objects.requireNonNull(orders);this.inventory=Objects.requireNonNull(inventory);this.clock=Objects.requireNonNull(clock);}
    public synchronized Order cancel(long userId,String orderNumber,String reason){
        var current=orders.findByNumber(orderNumber).orElseThrow(()->new IllegalArgumentException("ORDER_NOT_FOUND"));
        if(current.userId()!=userId) throw new IllegalArgumentException("FORBIDDEN");
        if(current.status()==Order.Status.CANCELLED) return current;
        if(current.status()!=Order.Status.PENDING_PAYMENT) throw new IllegalArgumentException("ORDER_NOT_CANCELLABLE");
        for(var item:current.items()) inventory.release(item.reservationKey());
        var cancelled=new Order(current.orderNumber(),current.userId(),current.kind(),current.activityId(),current.itemSubtotalMinor(),current.activityDiscountMinor(),current.couponDiscountMinor(),current.payableAmountMinor(),current.currency(),Order.Status.CANCELLED,current.expiresAt(),current.items());
        return orders.save(cancelled);
    }
    public Order cancelIfExpired(long userId,String orderNumber){
        var o=orders.findByNumber(orderNumber).orElseThrow();
        if(o.expiresAt().isAfter(OffsetDateTime.now(clock))) throw new IllegalArgumentException("ORDER_NOT_EXPIRED");
        return cancel(userId,orderNumber,"PAYMENT_TIMEOUT");
    }
}
