package com.flashsale.activity;
import java.time.OffsetDateTime;
public record Activity(long id,String name,long productId,long salePriceMinor,int initialStock,int availableStock,int purchaseLimitPerUser,OffsetDateTime startsAt,OffsetDateTime endsAt,ActivityStatus status,boolean preheated,long updatedBy) {}
