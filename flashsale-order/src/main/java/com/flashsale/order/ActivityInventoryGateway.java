package com.flashsale.order;

public interface ActivityInventoryGateway {
    Reservation reserve(long activityId, long userId, int quantity);
    void release(Reservation reservation);
    record Reservation(String key, long activityId, long userId, int quantity) {}
}
