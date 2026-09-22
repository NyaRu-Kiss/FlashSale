package com.flashsale.order;

public interface CouponGateway {
    CouponSnapshot getCoupon(long userId, long couponId);

    record CouponSnapshot(long couponId, long userId, long thresholdMinor, long discountMinor,
                          boolean available) {}
}
