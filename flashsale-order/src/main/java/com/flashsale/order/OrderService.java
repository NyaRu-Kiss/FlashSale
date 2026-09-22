package com.flashsale.order;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Application service; persistence implementations can replace the in-memory ports. */
public final class OrderService {
    private final OrderPreviewService previewService;
    private final InventoryGateway inventory;
    private final OrderRepository orders;
    private final Clock clock;
    private final Map<String, Submission> submissions = new ConcurrentHashMap<>();

    public OrderService(OrderPreviewService previewService, InventoryGateway inventory,
                        OrderRepository orders, Clock clock) {
        this.previewService = Objects.requireNonNull(previewService);
        this.inventory = Objects.requireNonNull(inventory);
        this.orders = Objects.requireNonNull(orders);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized Order create(OrderCreateRequest request) {
        if (request == null || request.userId() <= 0 || request.idempotencyKey() == null || request.idempotencyKey().isBlank())
            throw new IllegalArgumentException("VALIDATION_ERROR");
        String fingerprint = request.preview().toString();
        String key = request.userId() + ":" + request.idempotencyKey();
        Submission prior = submissions.get(key);
        if (prior != null) {
            if (!prior.fingerprint().equals(fingerprint)) throw new IllegalArgumentException("IDEMPOTENCY_CONFLICT");
            return orders.findByNumber(prior.orderNumber()).orElseThrow();
        }
        var preview = previewService.preview(request.preview());
        String number = "O" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        List<InventoryGateway.Reservation> reservations;
        try {
            reservations = inventory.reserve(request.userId(), number,
                    preview.items().stream().map(i -> new InventoryGateway.ReservationRequest(i.productId(), i.quantity())).toList());
        } catch (RuntimeException ex) {
            throw ex;
        }
        var byProduct = reservations.stream().iterator();
        var items = new ArrayList<Order.Item>();
        for (var item : preview.items()) {
            if (!byProduct.hasNext()) throw new IllegalStateException("INVENTORY_RESERVATION_MISMATCH");
            items.add(new Order.Item(item.productId(), item.quantity(), item.sku(), item.productName(),
                    item.listPriceMinor(), item.salePriceMinor(), item.activityDiscountMinor(), item.lineAmountMinor(), byProduct.next().reservationKey()));
        }
        var order = new Order(number, request.userId(), preview.kind(), request.preview().activityId(),
                preview.itemSubtotalMinor(), preview.activityDiscountMinor(), preview.couponDiscountMinor(),
                preview.payableAmountMinor(), "CNY", Order.Status.PENDING_PAYMENT,
                OffsetDateTime.now(clock).plus(Duration.ofMinutes(15)), List.copyOf(items));
        orders.save(order);
        submissions.put(key, new Submission(fingerprint, number));
        return order;
    }

    private record Submission(String fingerprint, String orderNumber) {}
}
