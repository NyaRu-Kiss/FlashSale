package com.flashsale.activity;

/** High-concurrency inventory projection; PostgreSQL remains the authority. */
interface ActivityInventoryPort {
    void preheat(Activity activity);
    Reservation reserve(Activity activity, long userId, int quantity, String reservationKey);
    boolean release(Activity activity, long userId, int quantity, String reservationKey);
    void closeGate(long activityId);
    void rebuild(Activity activity, int availableStock);
    record Reservation(boolean accepted, int remainingStock, String reason) {}
}
