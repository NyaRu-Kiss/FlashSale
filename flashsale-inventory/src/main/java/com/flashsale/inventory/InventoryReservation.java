package com.flashsale.inventory;
public record InventoryReservation(String key,long resourceId,int quantity,ReservationStatus status) {}
