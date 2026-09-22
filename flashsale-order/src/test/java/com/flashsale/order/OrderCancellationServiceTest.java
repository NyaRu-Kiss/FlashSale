package com.flashsale.order;
import org.junit.jupiter.api.Test; import java.time.*; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
class OrderCancellationServiceTest {
 @Test void cancellationReleasesOnlyOnce(){
  var repo=new InMemoryOrderRepository(); var released=new ArrayList<String>(); var inv=new InventoryGateway(){public List<Reservation> reserve(long u,String o,List<ReservationRequest> r){return List.of(new Reservation("r",1,1));} public void release(String k){released.add(k);} public void confirm(String k){}};
  var o=new Order("o",1,OrderKind.DIRECT,null,1,0,0,1,"CNY",Order.Status.PENDING_PAYMENT,OffsetDateTime.now().plusMinutes(5),List.of(new Order.Item(1,1,"s","p",1,1,0,1,"r"))); repo.save(o);
  var s=new OrderCancellationService(repo,inv,Clock.systemUTC()); assertEquals(Order.Status.CANCELLED,s.cancel(1,"o","USER_CANCEL").status()); assertEquals(Order.Status.CANCELLED,s.cancel(1,"o","USER_CANCEL").status()); assertEquals(1,released.size());
 }
}
