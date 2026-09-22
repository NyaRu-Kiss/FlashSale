package com.flashsale.order;

import java.util.List;

public interface InventoryGateway {
    List<Reservation> reserve(long userId, String orderNumber, List<ReservationRequest> requests);
    void release(String reservationKey);
    void confirm(String reservationKey);

    record ReservationRequest(long productId, int quantity) {}
    record Reservation(String reservationKey, long productId, int quantity) {}
}
