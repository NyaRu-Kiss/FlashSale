package com.flashsale.order;

import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findByNumber(String orderNumber);
}
