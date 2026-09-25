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
        List<Order> rows = jdbc.query("select order_number,user_id,kind,activity_id,item_subtotal_minor,activity_discount_minor,coupon_discount_minor,payable_amount_minor,currency,status,expires_at from customer_order where user_id=? order by id desc limit ? offset ?",
                (rs, row) -> new Order(rs.getString(1), rs.getLong(2), OrderKind.valueOf(rs.getString(3)), rs.getObject(4, Long.class), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getString(9), Order.Status.valueOf(rs.getString(10)), rs.getObject(11, OffsetDateTime.class), List.of()), userId, pageSize, (page - 1) * pageSize);
        return new PageResponse<>(rows, page, pageSize, total);
    }

    public Order get(long userId, String number) {
        List<Order> rows = jdbc.query("select order_number,user_id,kind,activity_id,item_subtotal_minor,activity_discount_minor,coupon_discount_minor,payable_amount_minor,currency,status,expires_at from customer_order where order_number=? and user_id=?",
                (rs, row) -> new Order(rs.getString(1), rs.getLong(2), OrderKind.valueOf(rs.getString(3)), rs.getObject(4, Long.class), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getString(9), Order.Status.valueOf(rs.getString(10)), rs.getObject(11, OffsetDateTime.class), List.of()), number, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("ORDER_NOT_FOUND");
        return rows.getFirst();
    }
}
