package com.flashsale.order;

import java.time.Clock;
import java.time.OffsetDateTime;

public final class ActivityOrderValidator {
    private final Clock clock;
    public ActivityOrderValidator(Clock clock){this.clock=clock;}
    public void validate(OrderPreviewRequest request, ActivityGateway.ActivitySnapshot activity){
        if(request.kind()!=OrderKind.ACTIVITY || activity==null) throw new IllegalArgumentException("ACTIVITY_REQUIRED");
        if(!activity.activeAt(OffsetDateTime.now(clock))) throw new IllegalArgumentException("ACTIVITY_NOT_ACTIVE");
        if(request.items().size()!=1 || request.items().getFirst().productId()!=activity.productId()) throw new IllegalArgumentException("ACTIVITY_ORDER_SINGLE_ITEM_REQUIRED");
        if(request.items().getFirst().quantity()>activity.purchaseLimitPerUser()) throw new IllegalArgumentException("PURCHASE_LIMIT_EXCEEDED");
    }
}
