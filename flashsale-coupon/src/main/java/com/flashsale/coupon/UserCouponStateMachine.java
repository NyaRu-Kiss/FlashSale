package com.flashsale.coupon;
public final class UserCouponStateMachine {
 public UserCouponStatus reserve(UserCouponStatus s){if(s!=UserCouponStatus.AVAILABLE)throw invalid(s);return UserCouponStatus.RESERVED;}
 public UserCouponStatus consume(UserCouponStatus s){if(s!=UserCouponStatus.RESERVED)throw invalid(s);return UserCouponStatus.CONSUMED;}
 public UserCouponStatus release(UserCouponStatus s,boolean usable){if(s!=UserCouponStatus.RESERVED)throw invalid(s);return usable?UserCouponStatus.AVAILABLE:UserCouponStatus.EXPIRED;}
 public UserCouponStatus expire(UserCouponStatus s){if(s==UserCouponStatus.CONSUMED||s==UserCouponStatus.EXPIRED)throw invalid(s);return UserCouponStatus.EXPIRED;}
 private IllegalArgumentException invalid(UserCouponStatus s){return new IllegalArgumentException("INVALID_COUPON_STATE:"+s);}
}
