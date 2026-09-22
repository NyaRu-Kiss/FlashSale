package com.flashsale.coupon;
import java.time.OffsetDateTime;
public record CouponTemplate(long id,String name,long thresholdMinor,long discountMinor,int issueLimit,int claimLimitPerUser,OffsetDateTime claimStartsAt,OffsetDateTime claimEndsAt,OffsetDateTime useStartsAt,OffsetDateTime useEndsAt,String status,long updatedBy){}
