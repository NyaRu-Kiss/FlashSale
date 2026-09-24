package com.flashsale.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.trace.TraceContext;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** All durable writes for one submission use the same PostgreSQL transaction. */
public final class JdbcOrderCreationStore implements OrderCreationStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final OrderActivityInventory activityInventory;
    private final ObjectMapper json;

    public JdbcOrderCreationStore(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                  OrderActivityInventory activityInventory, ObjectMapper json) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.activityInventory = activityInventory;
        this.json = json;
    }

    @Override public Order create(OrderCreateRequest request, String fingerprint, Supplier<OrderPreview> previewSupplier) {
        return transactions.execute(status -> createInTransaction(request, fingerprint, previewSupplier));
    }

    private Order createInTransaction(OrderCreateRequest request, String fingerprint, Supplier<OrderPreview> previewSupplier) {
        int claimed = jdbc.update("""
                insert into order_submission_idempotency(user_id,idempotency_key,request_fingerprint,status)
                values (?, ?, ?, 'PROCESSING') on conflict (user_id,idempotency_key) do nothing
                """, request.userId(), request.idempotencyKey(), fingerprint);
        if (claimed == 0) return existing(request, fingerprint);
        OrderPreview preview = previewSupplier.get();
        String number = "O" + UUID.randomUUID().toString().replace("-", "");
        Long activityId = request.preview().activityId();
        int limit = 0;
        if (preview.kind() == OrderKind.ACTIVITY) {
            if (preview.items().size() != 1 || activityId == null) throw error("ACTIVITY_ORDER_SINGLE_ITEM_REQUIRED");
            List<Integer> limits = jdbc.query("""
                    select purchase_limit_per_user from marketing_activity
                    where id = ? and product_id = ? and status = 'ACTIVE'
                      and starts_at <= current_timestamp and ends_at > current_timestamp
                    """, (rs, row) -> rs.getInt(1), activityId, preview.items().getFirst().productId());
            if (limits.isEmpty()) throw error("ACTIVITY_NOT_ACTIVE");
            limit = limits.getFirst();
            activityInventory.reserve(activityId, request.userId(), preview.items().getFirst().quantity(), limit, number);
            int quantity = preview.items().getFirst().quantity();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int completion) {
                    if (completion == STATUS_COMMITTED) activityInventory.complete(activityId, number);
                    else activityInventory.compensate(activityId, request.userId(), quantity, number);
                }
            });
        }
        OffsetDateTime createdAt = jdbc.queryForObject("select current_timestamp", OffsetDateTime.class);
        OffsetDateTime expiry = createdAt.plus(Duration.ofMinutes(15));
        Long orderId = jdbc.queryForObject("""
                insert into customer_order(order_number,user_id,kind,activity_id,item_subtotal_minor,
                    activity_discount_minor,coupon_discount_minor,payable_amount_minor,currency,expires_at)
                values (?,?,?::order_kind,?,?,?,?,?,'CNY',?) returning id
                """, Long.class, number, request.userId(), preview.kind().name(), activityId,
                preview.itemSubtotalMinor(), preview.activityDiscountMinor(), preview.couponDiscountMinor(),
                preview.payableAmountMinor(), expiry);
        if (orderId == null) throw error("ORDER_INSERT_FAILED");
        if (activityId != null) {
            jdbc.update("""
                    insert into activity_user_quota(activity_id,user_id,committed_quantity)
                    values (?,?,0) on conflict (activity_id,user_id) do nothing
                    """, activityId, request.userId());
            if (jdbc.update("""
                    update activity_user_quota set committed_quantity = committed_quantity + ?
                    where activity_id = ? and user_id = ? and committed_quantity + ? <= ?
                    """, preview.items().getFirst().quantity(), activityId, request.userId(),
                    preview.items().getFirst().quantity(), limit) != 1) throw error("ACTIVITY_PURCHASE_LIMIT_EXCEEDED");
        }
        List<Order.Item> items = new ArrayList<>();
        for (OrderPreview.Item item : preview.items()) {
            Long itemId = jdbc.queryForObject("""
                    insert into order_item(order_id,product_id,quantity,sku_snapshot,product_name_snapshot,
                        list_price_minor,sale_price_minor,activity_discount_minor,line_amount_minor)
                    values (?,?,?,?,?,?,?,?,?) returning id
                    """, Long.class, orderId, item.productId(), item.quantity(), item.sku(), item.productName(),
                    item.listPriceMinor(), item.salePriceMinor(), item.activityDiscountMinor(), item.lineAmountMinor());
            if (itemId == null) throw error("ORDER_ITEM_INSERT_FAILED");
            Long sequence = null;
            if (activityId == null) {
                if (jdbc.update("""
                        update product set available_stock = available_stock - ?, version = version + 1
                        where id = ? and status = 'ON_SALE' and available_stock >= ?
                        """, item.quantity(), item.productId(), item.quantity()) != 1) throw error("STOCK_NOT_ENOUGH");
            } else {
                sequence = jdbc.queryForObject("""
                        update activity_inventory_sequence set next_event_sequence = next_event_sequence + 1,
                            updated_at = now() where activity_id = ? returning next_event_sequence - 1
                        """, Long.class, activityId);
                if (sequence == null) throw error("ACTIVITY_NOT_FOUND");
            }
            String reservationKey = number + ":" + itemId;
            Long reservationId = jdbc.queryForObject("""
                    insert into inventory_reservation(order_item_id,source,product_id,activity_id,
                        activity_reserve_sequence,quantity)
                    values (?,?::inventory_source,?,?,?,?) returning id
                    """, Long.class, itemId, activityId == null ? "PRODUCT" : "ACTIVITY",
                    activityId == null ? item.productId() : null, activityId, sequence, item.quantity());
            if (reservationId == null) throw error("RESERVATION_INSERT_FAILED");
            jdbc.update("""
                    insert into inventory_movement(reservation_id,source,product_id,activity_id,
                        activity_inventory_event_sequence,quantity_delta,reason)
                    values (?,?::inventory_source,?,?,?,?,'RESERVE')
                    """, reservationId, activityId == null ? "PRODUCT" : "ACTIVITY",
                    activityId == null ? item.productId() : null, activityId, sequence, -item.quantity());
            if (activityId != null) appendActivityEvent(activityId, sequence, reservationId, item.quantity(), number);
            items.add(new Order.Item(item.productId(), item.quantity(), item.sku(), item.productName(),
                    item.listPriceMinor(), item.salePriceMinor(), item.activityDiscountMinor(), item.lineAmountMinor(), reservationKey));
        }
        if (request.preview().couponId() != null) lockCoupon(orderId, request, preview);
        UUID createdEvent = UUID.randomUUID();
        outbox(createdEvent, "ORDER_CREATED", "ORDER_CREATED:" + number, "ORDER", number,
                Map.of("event_id", createdEvent.toString(), "idempotency_key", "ORDER_CREATED:" + number,
                        "event_type", "ORDER_CREATED", "producer", "flashsale-order", "aggregate_id", number,
                        "created_at", createdAt.toString(), "order_number", number,
                        "user_id", request.userId(), "trace_id", TraceContext.getOrCreate()));
        jdbc.update("""
                update order_submission_idempotency set status='SUCCEEDED',order_id=?,response_code='OK',completed_at=now()
                where user_id=? and idempotency_key=? and status='PROCESSING'
                """, orderId, request.userId(), request.idempotencyKey());
        return new Order(number, request.userId(), preview.kind(), activityId, preview.itemSubtotalMinor(),
                preview.activityDiscountMinor(), preview.couponDiscountMinor(), preview.payableAmountMinor(),
                "CNY", Order.Status.PENDING_PAYMENT, expiry, List.copyOf(items));
    }

    private void lockCoupon(long orderId, OrderCreateRequest request, OrderPreview preview) {
        Long couponId = request.preview().couponId();
        List<long[]> coupons = jdbc.query("""
                select t.threshold_minor,t.discount_minor from user_coupon c
                join coupon_template t on t.id = c.coupon_template_id
                where c.id=? and c.user_id=? and c.status='AVAILABLE'
                  and t.use_starts_at <= current_timestamp and t.use_ends_at > current_timestamp
                for update of c
                """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)}, couponId, request.userId());
        if (coupons.isEmpty()) throw error("COUPON_NOT_AVAILABLE");
        long[] snapshot = coupons.getFirst();
        if (preview.itemSubtotalMinor() - preview.activityDiscountMinor() < snapshot[0]
                || preview.couponDiscountMinor() != Math.min(snapshot[1],
                preview.itemSubtotalMinor() - preview.activityDiscountMinor())) throw error("COUPON_THRESHOLD_NOT_MET");
        if (jdbc.update("""
                update user_coupon set status='RESERVED',reserved_at=now(),version=version+1,updated_at=now()
                where id=? and user_id=? and status='AVAILABLE'
                """, couponId, request.userId()) != 1) throw error("COUPON_NOT_AVAILABLE");
        jdbc.update("""
                insert into coupon_reservation(order_id,user_coupon_id,user_id,threshold_snapshot_minor,discount_snapshot_minor)
                values (?,?,?,?,?)
                """, orderId, couponId, request.userId(), snapshot[0], preview.couponDiscountMinor());
    }

    private void appendActivityEvent(long activityId, long sequence, long reservationId, int quantity, String number) {
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                insert into activity_inventory_event(event_id,activity_id,event_sequence,reservation_id,
                    kind,quantity_delta,producer,outbox_event_id)
                values (?,?,?,?,'RESERVE',?,'order',?)
                """, eventId, activityId, sequence, reservationId, -quantity, eventId);
        outbox(eventId, "ACTIVITY_INVENTORY_RESERVE", "ORDER:ACTIVITY_INVENTORY:" + eventId,
                "ACTIVITY", Long.toString(activityId),
                Map.ofEntries(Map.entry("event_id", eventId.toString()),
                        Map.entry("idempotency_key", "ORDER:ACTIVITY_INVENTORY:" + eventId),
                        Map.entry("activity_id", activityId), Map.entry("aggregate_id", Long.toString(activityId)),
                        Map.entry("sequence", sequence), Map.entry("kind", "RESERVE"),
                        Map.entry("quantity", quantity), Map.entry("order_number", number),
                        Map.entry("trace_id", TraceContext.getOrCreate()),
                        Map.entry("event_type", "ACTIVITY_INVENTORY_RESERVE"),
                        Map.entry("producer", "flashsale-order"),
                        Map.entry("created_at", OffsetDateTime.now().toString())));
    }

    private void outbox(UUID eventId, String type, String key, String aggregate, String id, Map<String, ?> payload) {
        try {
            jdbc.update("""
                    insert into order_outbox(event_id,event_type,idempotency_key,aggregate_type,aggregate_id,payload,trace_id)
                    values (?,?,?,?,?,?::jsonb,?)
                    """, eventId, type, key, aggregate, id, json.writeValueAsString(payload), TraceContext.getOrCreate());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("OUTBOX_SERIALIZATION_FAILED", exception);
        }
    }

    private Order existing(OrderCreateRequest request, String fingerprint) {
        List<Submission> found = jdbc.query("""
                select request_fingerprint,status,order_id from order_submission_idempotency
                where user_id=? and idempotency_key=?
                """, (rs, row) -> new Submission(rs.getString(1), rs.getString(2), rs.getObject(3, Long.class)),
                request.userId(), request.idempotencyKey());
        if (found.isEmpty()) throw error("IDEMPOTENCY_RETRY");
        Submission prior = found.getFirst();
        if (!prior.fingerprint.equals(fingerprint)) throw error("IDEMPOTENCY_CONFLICT");
        if (!"SUCCEEDED".equals(prior.status) || prior.orderId == null) throw error("IDEMPOTENCY_PROCESSING");
        return load(prior.orderId);
    }

    private Order load(long id) {
        List<Order> headers = jdbc.query("""
                select order_number,user_id,kind,activity_id,item_subtotal_minor,activity_discount_minor,
                    coupon_discount_minor,payable_amount_minor,currency,status,expires_at
                from customer_order where id=?
                """, (rs, row) -> new Order(rs.getString(1), rs.getLong(2), OrderKind.valueOf(rs.getString(3)),
                rs.getObject(4, Long.class), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8),
                rs.getString(9), Order.Status.valueOf(rs.getString(10)), rs.getObject(11, OffsetDateTime.class), List.of()), id);
        if (headers.isEmpty()) throw error("ORDER_NOT_FOUND");
        Order h = headers.getFirst();
        List<Order.Item> items = jdbc.query("""
                select i.id,i.product_id,i.quantity,i.sku_snapshot,i.product_name_snapshot,i.list_price_minor,
                    i.sale_price_minor,i.activity_discount_minor,i.line_amount_minor
                from order_item i where i.order_id=? order by i.id
                """, (rs, row) -> new Order.Item(rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getString(5),
                rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), h.orderNumber() + ":" + rs.getLong(1)), id);
        return new Order(h.orderNumber(), h.userId(), h.kind(), h.activityId(), h.itemSubtotalMinor(),
                h.activityDiscountMinor(), h.couponDiscountMinor(), h.payableAmountMinor(), h.currency(),
                h.status(), h.expiresAt(), List.copyOf(items));
    }

    private record Submission(String fingerprint, String status, Long orderId) {}
    private static IllegalArgumentException error(String code) { return new IllegalArgumentException(code); }
}
