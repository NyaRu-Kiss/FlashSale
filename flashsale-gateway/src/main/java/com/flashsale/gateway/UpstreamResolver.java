package com.flashsale.gateway;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Keeps URL-to-service ownership explicit at the HTTP boundary. */
@Component
final class UpstreamResolver {
    private final Map<String, String> bases;

    UpstreamResolver(
            @Value("${flashsale.gateway.auth-url:http://auth:8080}") String authUrl,
            @Value("${flashsale.gateway.product-url:http://product:8080}") String productUrl,
            @Value("${flashsale.gateway.activity-url:http://activity:8080}") String activityUrl,
            @Value("${flashsale.gateway.coupon-url:http://coupon:8080}") String couponUrl,
            @Value("${flashsale.gateway.order-url:http://order:8080}") String orderUrl,
            @Value("${flashsale.gateway.payment-url:http://payment:8080}") String paymentUrl) {
        bases = Map.of("auth", authUrl, "product", productUrl, "activity", activityUrl,
                "coupon", couponUrl, "order", orderUrl, "payment", paymentUrl);
    }

    String baseFor(String path) {
        if (path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/admin/users")) return bases.get("auth");
        if (path.startsWith("/api/v1/products") || path.startsWith("/api/v1/admin/products")) return bases.get("product");
        if (path.startsWith("/api/v1/activities") || path.startsWith("/api/v1/admin/activities")) return bases.get("activity");
        if (path.startsWith("/api/v1/coupons") || path.startsWith("/api/v1/coupon-templates")
                || path.startsWith("/api/v1/admin/coupon-templates")) return bases.get("coupon");
        if (path.matches("/api/v1/orders/[^/]+/payments/?" ) || path.startsWith("/api/v1/payments/")) return bases.get("payment");
        if (path.startsWith("/api/v1/orders")) return bases.get("order");
        return null;
    }

    String base(String service) {
        return bases.get(service);
    }
}
