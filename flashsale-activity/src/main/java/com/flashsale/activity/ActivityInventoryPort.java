package com.flashsale.activity;

/** High-concurrency inventory projection; PostgreSQL remains the authority. */
interface ActivityInventoryPort {
    void preheat(Activity activity);
    Reservation reserve(Activity activity, long userId, int quantity, String reservationKey);
    boolean release(Activity activity, long userId, int quantity, String reservationKey);
    void closeGate(long activityId);
    /** Atomically reject new reservations, then wait for pre-gate admissions to settle. */
    void closeGateAndAwaitInFlight(long activityId);
    /** Called only after the admitted reservation committed or its Redis compensation succeeded. */
    void completeInFlight(long activityId, String reservationKey);
    boolean hasPreheatedKeys(long activityId);
    boolean activate(Activity activity);
    void rebuild(Activity activity, int availableStock);
    record Reservation(boolean accepted, int remainingStock, String reason) {}
}
