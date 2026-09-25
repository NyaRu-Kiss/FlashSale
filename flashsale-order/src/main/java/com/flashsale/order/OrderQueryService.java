package com.flashsale.order;

import com.flashsale.common.api.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public final class OrderQueryService {
    private final JdbcTemplate jdbc;
    public OrderQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PageResponse<Order> list(long userId, int page, int pageSize) {
        long total = jdbc.queryForObject("select count(*) from customer_order where user_id=?", Long.class, userId);
        List<Order> rows = jdbc.query("select id,order_number,user_id,kind,activity_id,item_subtotal_minor,activity_discount_minor,coupon_discount_minor,payable_amount_minor,currency,status,expires_at from customer_order where user_id=? order by id desc limit ? offset ?",
                (rs, row) -> load(rs.getLong(1), rs.getString(2), rs.getLong(3), OrderKind.valueOf(rs.getString(4)), rs.getObject(5, Long.class), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getString(10), Order.Status.valueOf(rs.getString(11)), rs.getObject(12, OffsetDateTime.class)), userId, pageSize, (page - 1) * pageSize);
        return new PageResponse<>(rows, page, pageSize, total);
    }

    public Order get(long userId, String number) {
        List<Order> rows = jdbc.query("select id,order_number,user_id,kind,activity_id,item_subtotal_minor,activity_discount_minor,coupon_discount_minor,payable_amount_minor,currency,status,expires_at from customer_order where order_number=? and user_id=?",
                (rs, row) -> load(rs.getLong(1), rs.getString(2), rs.getLong(3), OrderKind.valueOf(rs.getString(4)), rs.getObject(5, Long.class), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getString(10), Order.Status.valueOf(rs.getString(11)), rs.getObject(12, OffsetDateTime.class)), number, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("ORDER_NOT_FOUND");
        return rows.getFirst();
    }

    private Order load(long id, String number, long userId, OrderKind kind, Long activityId,
                       long subtotal, long activityDiscount, long couponDiscount, long payable,
                       String currency, Order.Status status, OffsetDateTime expiresAt) {
        List<Order.Item> items = jdbc.query("select id,product_id,quantity,sku_snapshot,product_name_snapshot,list_price_minor,sale_price_minor,activity_discount_minor,line_amount_minor from order_item where order_id=? order by id",
                (rs, row) -> new Order.Item(rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), number + ":" + rs.getLong(1)), id);
        return new Order(number, userId, kind, activityId, subtotal, activityDiscount, couponDiscount, payable, currency, status, expiresAt, List.copyOf(items));
    }
}
