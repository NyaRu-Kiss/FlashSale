package com.flashsale.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.trace.TraceContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The cancellation transition and every release side effect share one PostgreSQL transaction. */
public final class JdbcOrderCancellationStore implements OrderCancellationStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    public JdbcOrderCancellationStore(JdbcTemplate jdbc, PlatformTransactionManager manager, ObjectMapper json) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.json = json;
    }

    @Override public Order cancel(long userId, String orderNumber, String reason, OffsetDateTime now) {
        return transactions.execute(status -> cancelInTransaction(userId, orderNumber, reason, now));
    }

    @Override public Order cancelTimeout(String orderNumber, OffsetDateTime now) {
        return transactions.execute(status -> cancelInTransaction(null, orderNumber, "PAYMENT_TIMEOUT", now));
    }

    private Order cancelInTransaction(Long userId, String number, String reason, OffsetDateTime now) {
        List<Long> ids = jdbc.query("""
                update customer_order set status='CANCELLED',cancelled_at=current_timestamp,cancellation_reason=?
                 where order_number=? and (? is null or user_id=?) and status='PENDING_PAYMENT'
                   and (? <> 'PAYMENT_TIMEOUT' or expires_at <= ?) returning id
                """, (rs, row) -> rs.getLong(1), reason, number, userId, userId, reason, now);
        if (ids.isEmpty()) return finalState(userId, number);
        long orderId = ids.getFirst();
        List<Reservation> reservations = jdbc.query("""
                select r.id,r.source::text,r.product_id,r.activity_id,r.quantity
                  from inventory_reservation r join order_item i on i.id=r.order_item_id
                 where i.order_id=? and r.status='RESERVED'
                """, (rs, row) -> new Reservation(rs.getLong(1), rs.getString(2),
                rs.getObject(3, Long.class), rs.getObject(4, Long.class), rs.getInt(5)), orderId);
        for (Reservation reservation : reservations) release(orderId, number, reservation);
        releaseCoupon(orderId, now);
        return load(orderId);
    }

    private void release(long orderId, String number, Reservation r) {
        if (jdbc.update("update inventory_reservation set status='RELEASED',released_at=current_timestamp where id=? and status='RESERVED'", r.id) != 1) return;
        if ("PRODUCT".equals(r.source)) {
            jdbc.update("update product set available_stock=available_stock+?,version=version+1 where id=?", r.quantity, r.productId);
            jdbc.update("insert into inventory_movement(reservation_id,source,product_id,quantity_delta,reason) values (?, 'PRODUCT', ?, ?, 'RELEASE')", r.id, r.productId, r.quantity);
            return;
        }
        jdbc.update("""
                update activity_user_quota set committed_quantity=committed_quantity-?
                 where activity_id=? and user_id=(select user_id from customer_order where id=?) and committed_quantity>=?
                """, r.quantity, r.activityId, orderId, r.quantity);
        Long sequence = jdbc.queryForObject("""
                update activity_inventory_sequence set next_event_sequence=next_event_sequence+1,updated_at=now()
                 where activity_id=? returning next_event_sequence-1
                """, Long.class, r.activityId);
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                insert into activity_inventory_event(event_id,activity_id,event_sequence,reservation_id,kind,quantity_delta,producer,outbox_event_id)
                values (?,?,?,?,'RELEASE',?,'order',?)
                """, eventId, r.activityId, sequence, r.id, r.quantity, eventId);
        jdbc.update("""
                insert into inventory_movement(reservation_id,source,activity_id,activity_inventory_event_sequence,quantity_delta,reason)
                values (?, 'ACTIVITY', ?, ?, ?, 'RELEASE')
                """, r.id, r.activityId, sequence, r.quantity);
        outbox(eventId, "STOCK_RELEASE", "ORDER:STOCK_RELEASE:" + eventId, "ACTIVITY", Long.toString(r.activityId), Map.ofEntries(
                Map.entry("event_id", eventId.toString()), Map.entry("idempotency_key", "ORDER:STOCK_RELEASE:" + eventId),
                Map.entry("event_type", "STOCK_RELEASE"), Map.entry("producer", "flashsale-order"),
                Map.entry("activity_id", r.activityId),
                Map.entry("aggregate_id", Long.toString(r.activityId)), Map.entry("sequence", sequence),
                Map.entry("kind", "RELEASE"), Map.entry("quantity", r.quantity), Map.entry("order_number", number),
                Map.entry("trace_id", TraceContext.getOrCreate()), Map.entry("created_at", OffsetDateTime.now().toString())));
    }

    private void releaseCoupon(long orderId, OffsetDateTime now) {
        List<Long> coupons = jdbc.query("select user_coupon_id from coupon_reservation where order_id=? and status='RESERVED'", (rs, row) -> rs.getLong(1), orderId);
        for (Long couponId : coupons) {
            jdbc.update("update coupon_reservation set status='RELEASED',released_at=current_timestamp where order_id=? and user_coupon_id=? and status='RESERVED'", orderId, couponId);
            jdbc.update("""
                    update user_coupon set status=case when exists (
                        select 1 from coupon_template t where t.id=user_coupon.coupon_template_id and t.use_ends_at > ?
                    ) then 'AVAILABLE' else 'EXPIRED' end,reserved_at=null,expired_at=case when exists (
                        select 1 from coupon_template t where t.id=user_coupon.coupon_template_id and t.use_ends_at > ?
                    ) then null else current_timestamp end,version=version+1
                     where id=? and status='RESERVED'
                    """, now, now, couponId);
        }
    }

    private void outbox(UUID eventId, String type, String key, String aggregate, String id, Map<String, ?> payload) {
        try {
            jdbc.update("insert into order_outbox(event_id,event_type,idempotency_key,aggregate_type,aggregate_id,payload,trace_id) values (?,?,?,?,?,?::jsonb,?)",
                    eventId, type, key, aggregate, id, json.writeValueAsString(payload), TraceContext.getOrCreate());
        } catch (JsonProcessingException e) { throw new IllegalStateException("OUTBOX_SERIALIZATION_FAILED", e); }
    }

    private Order finalState(Long userId, String number) {
        List<Long> found = userId == null
                ? jdbc.query("select id from customer_order where order_number=?", (rs, row) -> rs.getLong(1), number)
                : jdbc.query("select id from customer_order where order_number=? and user_id=?", (rs, row) -> rs.getLong(1), number, userId);
        if (found.isEmpty()) throw new IllegalArgumentException("ORDER_NOT_FOUND");
        return load(found.getFirst());
    }

    private Order load(long id) {
        Order h = jdbc.queryForObject("select order_number,user_id,kind,activity_id,item_subtotal_minor,activity_discount_minor,coupon_discount_minor,payable_amount_minor,currency,status,expires_at from customer_order where id=?",
                (rs, row) -> new Order(rs.getString(1), rs.getLong(2), OrderKind.valueOf(rs.getString(3)), rs.getObject(4, Long.class), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getString(9), Order.Status.valueOf(rs.getString(10)), rs.getObject(11, OffsetDateTime.class), List.of()), id);
        List<Order.Item> items = jdbc.query("select i.id,i.product_id,i.quantity,i.sku_snapshot,i.product_name_snapshot,i.list_price_minor,i.sale_price_minor,i.activity_discount_minor,i.line_amount_minor from order_item i where i.order_id=? order by i.id",
                (rs, row) -> new Order.Item(rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), h.orderNumber()+":"+rs.getLong(1)), id);
        return new Order(h.orderNumber(), h.userId(), h.kind(), h.activityId(), h.itemSubtotalMinor(), h.activityDiscountMinor(), h.couponDiscountMinor(), h.payableAmountMinor(), h.currency(), h.status(), h.expiresAt(), List.copyOf(items));
    }

    private record Reservation(long id, String source, Long productId, Long activityId, int quantity) {}
}
