package com.flashsale.order;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryOrderRepository implements OrderRepository {
    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();
    public Order save(Order order) { orders.put(order.orderNumber(), order); return order; }
    public Optional<Order> findByNumber(String number) { return Optional.ofNullable(orders.get(number)); }
}
