package com.flashsale.order;

import com.flashsale.common.metrics.BusinessMetrics;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** The database store owns the creation transaction and idempotency claim. */
public final class OrderService {
    private final OrderCreationStore store;
    private final OrderPreviewService previews;
    private final BusinessMetrics metrics;

    public OrderService(OrderCreationStore store, OrderPreviewService previews) {
        this(store, previews, null);
    }

    public OrderService(OrderCreationStore store, OrderPreviewService previews, BusinessMetrics metrics) {
        this.store = Objects.requireNonNull(store);
        this.previews = Objects.requireNonNull(previews);
        this.metrics = metrics;
    }

    public Order create(OrderCreateRequest request) {
        if (request == null || request.userId() <= 0 || request.idempotencyKey() == null
                || !request.idempotencyKey().startsWith("ORDER_SUBMIT_") || request.idempotencyKey().length() > 128
                || request.preview() == null || request.preview().userId() != request.userId())
            throw new IllegalArgumentException("VALIDATION_ERROR");
        try {
            Order order = store.create(request, fingerprint(request.preview()), () -> previews.preview(request.preview()));
            if (metrics != null) metrics.order("create", "SUCCESS");
            return order;
        } catch (RuntimeException error) {
            if (metrics != null) metrics.order("create", error.getMessage() == null ? "ERROR" : error.getMessage());
            throw error;
        }
    }

    static String fingerprint(OrderPreviewRequest request) {
        StringBuilder canonical = new StringBuilder().append(request.userId()).append('|')
                .append(request.kind()).append('|').append(request.activityId()).append('|')
                .append(request.couponId());
        if (request.items() != null) for (var item : request.items())
            canonical.append('|').append(item == null ? "null" : item.productId() + ":" + item.quantity());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
