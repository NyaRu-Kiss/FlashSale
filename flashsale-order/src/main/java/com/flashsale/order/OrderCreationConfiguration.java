package com.flashsale.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.api.ApiResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Production adapters use service discovery for reads and PostgreSQL for the order ledger. */
@Configuration(proxyBeanMethods = false)
class OrderCreationConfiguration {
    @Bean Clock orderClock() { return Clock.systemUTC(); }
    @Bean OrderActivityInventory orderActivityInventory(StringRedisTemplate redis) {
        return new RedisOrderActivityInventory(redis);
    }
    @Bean OrderCreationStore orderCreationStore(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                               OrderActivityInventory activityInventory, ObjectMapper json) {
        return new JdbcOrderCreationStore(jdbc, manager, activityInventory, json);
    }
    @Bean OrderPreviewService orderPreviewService(ProductGateway products, ActivityGateway activities,
                                                 CouponGateway coupons, Clock clock) {
        return new OrderPreviewService(products, activities, coupons, clock);
    }
    @Bean OrderService orderService(OrderCreationStore store, OrderPreviewService previews) {
        return new OrderService(store, previews);
    }
    @Bean ProductGateway orderProductGateway(OrderProductClient client) {
        return id -> {
            ApiResponse<ProductView> response = client.get(id);
            ProductView p = require(response);
            return new ProductGateway.ProductSnapshot(p.id(), p.sku(), p.name(), p.priceMinor(),
                    p.priceMinor(), "ON_SALE".equals(p.status()));
        };
    }
    @Bean ActivityGateway orderActivityGateway(OrderActivityClient client) {
        return id -> {
            ActivityView a = require(client.get(id));
            return new ActivityGateway.ActivitySnapshot(a.id(), a.productId(), a.salePriceMinor(),
                    a.startsAt(), a.endsAt(), a.status(), a.purchaseLimitPerUser());
        };
    }
    @Bean CouponGateway orderCouponGateway(OrderCouponClient client) {
        return (userId, couponId) -> {
            List<CouponView> coupons = require(client.mine());
            return coupons.stream().filter(c -> c.id() == couponId)
                    .map(c -> new CouponGateway.CouponSnapshot(c.id(), userId, c.thresholdMinor(),
                            c.discountMinor(), "AVAILABLE".equals(c.status())
                                    && !OffsetDateTime.now().isBefore(c.useStartsAt())
                                    && OffsetDateTime.now().isBefore(c.useEndsAt())))
                    .findFirst().orElse(null);
        };
    }
    private static <T> T require(ApiResponse<T> response) {
        if (response == null || !"OK".equals(response.code()) || response.data() == null)
            throw new IllegalArgumentException(response == null ? "SERVICE_UNAVAILABLE" : response.code());
        return response.data();
    }

    record ProductView(long id, String sku, String name, long priceMinor, String status) {}
    record ActivityView(long id, long productId, long salePriceMinor, OffsetDateTime startsAt,
                        OffsetDateTime endsAt, String status, int purchaseLimitPerUser) {}
    record CouponView(long id, String status, long thresholdMinor, long discountMinor,
                      OffsetDateTime useStartsAt, OffsetDateTime useEndsAt) {}
}

@FeignClient(name = "flashsale-product", contextId = "orderProductClient")
interface OrderProductClient {
    @GetMapping("/api/v1/products/{id}") ApiResponse<OrderCreationConfiguration.ProductView> get(@PathVariable("id") long id);
}
@FeignClient(name = "flashsale-activity", contextId = "orderActivityClient")
interface OrderActivityClient {
    @GetMapping("/api/v1/activities/{id}") ApiResponse<OrderCreationConfiguration.ActivityView> get(@PathVariable("id") long id);
}
@FeignClient(name = "flashsale-coupon", contextId = "orderCouponClient")
interface OrderCouponClient {
    @GetMapping("/api/v1/coupons/me") ApiResponse<List<OrderCreationConfiguration.CouponView>> mine();
}
