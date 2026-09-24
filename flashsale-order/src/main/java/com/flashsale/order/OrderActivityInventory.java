package com.flashsale.order;

/** Redis admission for one activity order. The database remains the durable ledger. */
public interface OrderActivityInventory {
    void reserve(long activityId, long userId, int quantity, int limit, String reservationKey);
    void complete(long activityId, String reservationKey);
    void compensate(long activityId, long userId, int quantity, String reservationKey);
}
