package com.flashsale.activity;
import java.util.Objects;
public final class ActivityQueryService {
 public ActivityView publicView(Activity activity, ActivityMetrics metrics){Objects.requireNonNull(activity);Objects.requireNonNull(metrics);return new ActivityView(activity.id(),activity.name(),activity.productId(),activity.salePriceMinor(),metrics.availableStock(),activity.startsAt(),activity.endsAt(),visible(metrics.status()));}
 public ActivityMetrics metrics(Activity activity,ActivityInventoryLedger ledger){return new ActivityMetrics(activity.status(),activity.availableStock(),ledger.events().size(),ledger.checkpoint(),ledger.events().isEmpty()?0:ledger.events().get(ledger.events().size()-1).sequence(),ledger.recoverable());}
 private ActivityStatus visible(ActivityStatus s){return s==ActivityStatus.PAUSED?ActivityStatus.PAUSED:s;}
 public record ActivityView(long id,String name,long productId,long salePriceMinor,int availableStock,java.time.OffsetDateTime startsAt,java.time.OffsetDateTime endsAt,ActivityStatus status){}
}
