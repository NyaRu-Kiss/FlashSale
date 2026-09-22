package com.flashsale.order;

import java.time.OffsetDateTime;

public interface ActivityGateway {
    ActivitySnapshot getActivity(long activityId);

    record ActivitySnapshot(long activityId, long productId, long salePriceMinor,
                            OffsetDateTime startsAt, OffsetDateTime endsAt,
                            String status, int purchaseLimitPerUser) {
        public boolean activeAt(OffsetDateTime now) {
            return "ACTIVE".equals(status) && !now.isBefore(startsAt) && now.isBefore(endsAt);
        }
    }
}
