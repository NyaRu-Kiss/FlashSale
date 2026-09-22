package com.flashsale.order;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryActivityInventoryGateway implements ActivityInventoryGateway {
    private final Map<Long,Integer> stock = new ConcurrentHashMap<>();
    private final Map<String,Integer> userQuantity = new ConcurrentHashMap<>();
    private final Map<String,Reservation> reservations = new ConcurrentHashMap<>();
    public synchronized void initialize(long activityId, int quantity) { stock.put(activityId, quantity); }
    public synchronized Reservation reserve(long activityId,long userId,int quantity) {
        String userKey=activityId+":"+userId;
        if (stock.getOrDefault(activityId,0)<quantity) throw new IllegalArgumentException("STOCK_NOT_ENOUGH");
        int used=userQuantity.getOrDefault(userKey,0); // limit is enforced by the caller/activity snapshot
        var r=new Reservation(java.util.UUID.randomUUID().toString(),activityId,userId,quantity);
        stock.compute(activityId,(k,v)->v-quantity); userQuantity.put(userKey,used+quantity); reservations.put(r.key(),r); return r;
    }
    public synchronized void release(Reservation r) { if (r==null||!reservations.containsKey(r.key())) return; reservations.remove(r.key()); stock.compute(r.activityId(),(k,v)->v+r.quantity()); userQuantity.computeIfPresent(r.activityId()+":"+r.userId(),(k,v)->Math.max(0,v-r.quantity())); }
}
