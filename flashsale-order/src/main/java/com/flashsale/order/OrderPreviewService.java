package com.flashsale.order;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** Pure validation/calculation service. It never reserves resources or writes orders. */
public final class OrderPreviewService {
    private final ProductGateway products;
    private final ActivityGateway activities;
    private final CouponGateway coupons;
    private final Clock clock;

    public OrderPreviewService(ProductGateway products, ActivityGateway activities,
                               CouponGateway coupons, Clock clock) {
        this.products = Objects.requireNonNull(products);
        this.activities = Objects.requireNonNull(activities);
        this.coupons = Objects.requireNonNull(coupons);
        this.clock = Objects.requireNonNull(clock);
    }

    public OrderPreview preview(OrderPreviewRequest request) {
        validateRequest(request);
        var activity = request.kind() == OrderKind.ACTIVITY
                ? activities.getActivity(request.activityId()) : null;
        var now = OffsetDateTime.now(clock);
        if (activity != null && !activity.activeAt(now)) throw error("ACTIVITY_NOT_ACTIVE");
        if (request.kind() == OrderKind.ACTIVITY && request.items().size() != 1)
            throw error("ACTIVITY_ORDER_SINGLE_ITEM_REQUIRED");

        long subtotal = 0, activityDiscount = 0;
        var result = new java.util.ArrayList<OrderPreview.Item>();
        for (var input : request.items()) {
            var product = requireProduct(input.productId());
            if (!product.onSale()) throw error("PRODUCT_NOT_ON_SALE");
            if (activity != null && product.productId() != activity.productId())
                throw error("ACTIVITY_PRODUCT_MISMATCH");
            long unit = activity == null ? product.salePriceMinor() : activity.salePriceMinor();
            long line = Math.multiplyExact(unit, input.quantity());
            long lineList = Math.multiplyExact(product.listPriceMinor(), input.quantity());
            long discount = Math.max(0, lineList - line);
            subtotal = Math.addExact(subtotal, lineList);
            activityDiscount = Math.addExact(activityDiscount, discount);
            result.add(new OrderPreview.Item(product.productId(), input.quantity(), product.sku(),
                    product.name(), product.listPriceMinor(), unit, discount, line));
        }
        long couponDiscount = 0;
        if (request.couponId() != null) {
            var coupon = coupons.getCoupon(request.userId(), request.couponId());
            if (coupon == null || coupon.userId() != request.userId() || !coupon.available())
                throw error("COUPON_NOT_AVAILABLE");
            long afterActivity = subtotal - activityDiscount;
            if (afterActivity < coupon.thresholdMinor()) throw error("COUPON_THRESHOLD_NOT_MET");
            couponDiscount = Math.min(coupon.discountMinor(), afterActivity);
        }
        return new OrderPreview(request.kind(), subtotal, activityDiscount, couponDiscount,
                subtotal - activityDiscount - couponDiscount, List.copyOf(result));
    }

    private ProductGateway.ProductSnapshot requireProduct(long id) {
        if (id <= 0) throw error("VALIDATION_ERROR");
        var p = products.getProduct(id);
        if (p == null) throw error("PRODUCT_NOT_FOUND");
        return p;
    }
    private void validateRequest(OrderPreviewRequest r) {
        if (r == null || r.userId() <= 0 || r.kind() == null || r.items() == null || r.items().isEmpty())
            throw error("VALIDATION_ERROR");
        for (var i : r.items()) if (i == null || i.productId() <= 0 || i.quantity() <= 0)
            throw error("VALIDATION_ERROR");
        if (r.kind() == OrderKind.ACTIVITY && r.activityId() == null) throw error("VALIDATION_ERROR");
        if (r.kind() == OrderKind.DIRECT && r.activityId() != null) throw error("ORDER_KIND_MISMATCH");
    }
    private IllegalArgumentException error(String code) { return new IllegalArgumentException(code); }
}
