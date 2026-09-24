package com.flashsale.order;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    @Test void validatesIdentityBeforeCallingStore() {
        AtomicInteger calls = new AtomicInteger();
        OrderCreationStore store = (request, fingerprint, preview) -> { calls.incrementAndGet(); return null; };
        var products = (ProductGateway) id -> new ProductGateway.ProductSnapshot(id, "S"+id, "P"+id, 100, 100, true);
        var previews = new OrderPreviewService(products, id -> null, (u,c) -> null, Clock.systemUTC());
        var service = new OrderService(store, previews);
        var mismatched = new OrderCreateRequest(1, "ORDER_SUBMIT_k",
                new OrderPreviewRequest(2, OrderKind.DIRECT, null, null, List.of(new OrderPreviewRequest.Item(1, 1))));
        assertThrows(IllegalArgumentException.class, () -> service.create(mismatched));
        assertEquals(0, calls.get());
    }

    @Test void fingerprintChangesWithRequestContentAndIsSha256() {
        var one = new OrderPreviewRequest(1, OrderKind.DIRECT, null, null,
                List.of(new OrderPreviewRequest.Item(1, 1)));
        var two = new OrderPreviewRequest(1, OrderKind.DIRECT, null, null,
                List.of(new OrderPreviewRequest.Item(1, 2)));
        assertEquals(64, OrderService.fingerprint(one).length());
        assertEquals(OrderService.fingerprint(one), OrderService.fingerprint(one));
        assertNotEquals(OrderService.fingerprint(one), OrderService.fingerprint(two));
    }
}
