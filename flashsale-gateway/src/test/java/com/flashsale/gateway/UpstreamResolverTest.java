package com.flashsale.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class UpstreamResolverTest {
    private final UpstreamResolver resolver = new UpstreamResolver(
            "http://auth", "http://product", "http://activity", "http://coupon", "http://order", "http://payment");

    @Test
    void resolvesEveryPublicApiOwner() {
        assertEquals("http://auth", resolver.baseFor("/api/v1/auth/login"));
        assertEquals("http://product", resolver.baseFor("/api/v1/admin/products/1"));
        assertEquals("http://activity", resolver.baseFor("/api/v1/activities/1"));
        assertEquals("http://coupon", resolver.baseFor("/api/v1/coupons/me"));
        assertEquals("http://order", resolver.baseFor("/api/v1/orders/NO-1"));
        assertEquals("http://payment", resolver.baseFor("/api/v1/orders/NO-1/payments"));
    }

    @Test
    void rejectsUnknownApiPath() {
        assertNull(resolver.baseFor("/api/v1/unknown"));
    }
}
