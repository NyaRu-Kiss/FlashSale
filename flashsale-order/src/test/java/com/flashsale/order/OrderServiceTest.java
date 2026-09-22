package com.flashsale.order;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    private OrderService service(List<String> reserved) {
        var products = (ProductGateway) id -> new ProductGateway.ProductSnapshot(id, "S"+id, "P"+id, 100, 90, true);
        var activities = (ActivityGateway) id -> new ActivityGateway.ActivitySnapshot(id, 1, 50,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1), "ACTIVE", 1);
        var coupons = (CouponGateway) (u,c) -> null;
        var inv = new InventoryGateway() {
            public List<Reservation> reserve(long u,String o,List<ReservationRequest> rs) {
                return rs.stream().map(r -> { String k=o+":"+r.productId(); reserved.add(k); return new Reservation(k,r.productId(),r.quantity()); }).toList();
            }
            public void release(String k) {} public void confirm(String k) {}
        };
        return new OrderService(new OrderPreviewService(products, activities, coupons, Clock.systemUTC()), inv,
                new InMemoryOrderRepository(), Clock.systemUTC());
    }
    @Test void sameKeySameRequestReturnsSameOrder() {
        var s = service(new ArrayList<>());
        var r = new OrderCreateRequest(1,"k",new OrderPreviewRequest(1,OrderKind.DIRECT,null,null,List.of(new OrderPreviewRequest.Item(1,1))));
        assertEquals(s.create(r), s.create(r));
    }
    @Test void sameKeyDifferentRequestConflicts() {
        var s = service(new ArrayList<>());
        s.create(new OrderCreateRequest(1,"k",new OrderPreviewRequest(1,OrderKind.DIRECT,null,null,List.of(new OrderPreviewRequest.Item(1,1)))));
        assertThrows(IllegalArgumentException.class, () -> s.create(new OrderCreateRequest(1,"k",new OrderPreviewRequest(1,OrderKind.DIRECT,null,null,List.of(new OrderPreviewRequest.Item(2,1))))));
    }
}
