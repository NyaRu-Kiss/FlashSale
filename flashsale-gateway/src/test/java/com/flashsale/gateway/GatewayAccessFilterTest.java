package com.flashsale.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.flashsale.common.security.JwtTokenService;
import com.flashsale.common.security.Principal;
import com.flashsale.common.security.Role;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GatewayAccessFilterTest {
    @Test
    void normalizesParameterizedResourcesWithoutLosingHotspotId() {
        String activity = "/api/v1/activities/activity-7/orders";
        String coupon = "/api/v1/coupons/template-9/claims";

        assertEquals("/api/v1/activities/{id}/orders", GatewayAccessFilter.normalizedPath(activity));
        assertEquals("activity-7", GatewayAccessFilter.pathParameter(activity, "/api/v1/activities/([^/]+)/orders"));
        assertEquals("/api/v1/coupons/{templateId}/claims", GatewayAccessFilter.normalizedPath(coupon));
        assertEquals("template-9", GatewayAccessFilter.pathParameter(coupon, "/api/v1/coupons/([^/]+)/claims"));
        assertNull(GatewayAccessFilter.pathParameter("/api/v1/coupons/template-9", "/api/v1/coupons/([^/]+)/claims"));
    }

    @Test
    void issuesTokensForTheThreeExplicitRoles() {
        JwtTokenService tokens = new JwtTokenService("01234567890123456789012345678901", Duration.ofMinutes(5));
        for (Role role : Role.values()) {
            Principal principal = new Principal(1L, role);
            assertEquals(principal, tokens.parse(tokens.issue(principal)));
        }
    }
}
