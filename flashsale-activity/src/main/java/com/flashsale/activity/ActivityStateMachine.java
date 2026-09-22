package com.flashsale.activity;
import java.time.OffsetDateTime;
public final class ActivityStateMachine {
 public Activity create(Activity a){if(a==null||a.name()==null||a.name().isBlank()||a.productId()<=0||a.salePriceMinor()<0||a.initialStock()<=0||a.purchaseLimitPerUser()<=0||a.startsAt()==null||a.endsAt()==null||!a.startsAt().isBefore(a.endsAt()))throw new IllegalArgumentException("VALIDATION_ERROR");return new Activity(a.id(),a.name(),a.productId(),a.salePriceMinor(),a.initialStock(),a.initialStock(),a.purchaseLimitPerUser(),a.startsAt(),a.endsAt(),ActivityStatus.NOT_STARTED,false,a.updatedBy());}
 public Activity preheat(Activity a){require(a,ActivityStatus.NOT_STARTED);return copy(a,a.status(),true);}
 public Activity start(Activity a,OffsetDateTime now){require(a,ActivityStatus.NOT_STARTED);if(!a.preheated()||now.isBefore(a.startsAt())||!now.isBefore(a.endsAt()))throw new IllegalArgumentException("ACTIVITY_NOT_READY");return copy(a,ActivityStatus.ACTIVE,a.preheated());}
 public Activity end(Activity a,OffsetDateTime now){if(a.status()!=ActivityStatus.ACTIVE||now.isBefore(a.endsAt()))throw new IllegalArgumentException("ACTIVITY_NOT_ACTIVE");return copy(a,ActivityStatus.ENDED,a.preheated());}
 public Activity cancel(Activity a){require(a,ActivityStatus.NOT_STARTED);return copy(a,ActivityStatus.CANCELLED,a.preheated());}
 private void require(Activity a,ActivityStatus s){if(a==null||a.status()!=s)throw new IllegalArgumentException("INVALID_ACTIVITY_STATE");} private Activity copy(Activity a,ActivityStatus s,boolean p){return new Activity(a.id(),a.name(),a.productId(),a.salePriceMinor(),a.initialStock(),a.availableStock(),a.purchaseLimitPerUser(),a.startsAt(),a.endsAt(),s,p,a.updatedBy());}
}
