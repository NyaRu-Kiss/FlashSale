package com.flashsale.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Run with FLASHSALE_TEST_JDBC_URL against a temporary PostgreSQL 16 initialized from V1. */
class JdbcOrderCreationStoreIntegrationTest {
    private JdbcTemplate jdbc;
    private long customer;
    private long operator;
    private long product;
    private FakeActivityRedis redis;
    private JdbcOrderCreationStore store;

    @BeforeEach void setup() {
        String url = System.getenv("FLASHSALE_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank());
        DataSource source = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("FLASHSALE_TEST_JDBC_USER", "postgres"),
                System.getenv().getOrDefault("FLASHSALE_TEST_JDBC_PASSWORD", "postgres"));
        jdbc = new JdbcTemplate(source);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        operator = jdbc.queryForObject("insert into app_user(username,password_hash,role) values (?,?, 'OPERATOR') returning id",
                Long.class, "op-" + suffix, "hash");
        customer = jdbc.queryForObject("insert into app_user(username,password_hash) values (?,?) returning id",
                Long.class, "customer-" + suffix, "hash");
        product = jdbc.queryForObject("""
                insert into product(sku,name,list_price_minor,available_stock,status,created_by,updated_by)
                values (?,?,100,3,'ON_SALE',?,?) returning id
                """, Long.class, "sku-" + suffix, "Product", operator, operator);
        redis = new FakeActivityRedis();
        store = new JdbcOrderCreationStore(jdbc, new DataSourceTransactionManager(source), redis,
                new ObjectMapper());
    }

    @Test void directOrderIsAtomicAndIdempotent() {
        var request = request("ORDER_SUBMIT_direct", OrderKind.DIRECT, null, null, 2);
        Order first = create(request);
        assertEquals(first, create(request));
        assertThrows(IllegalArgumentException.class, () -> create(request("ORDER_SUBMIT_direct", OrderKind.DIRECT, null, null, 1)));
        assertEquals(1, value("select count(*) from customer_order where user_id=?", customer));
        assertEquals(1, value("select available_stock from product where id=?", product));
        assertEquals(1, value("select count(*) from inventory_reservation r join order_item i on i.id=r.order_item_id join customer_order o on o.id=i.order_id where o.user_id=?", customer));
        assertEquals(1, value("select count(*) from order_outbox where aggregate_id=?", first.orderNumber()));
        assertThrows(IllegalArgumentException.class, () -> create(request("ORDER_SUBMIT_short", OrderKind.DIRECT, null, null, 2)));
        assertEquals(0, value("select count(*) from order_submission_idempotency where idempotency_key='ORDER_SUBMIT_short'"));
        assertEquals(1, value("select available_stock from product where id=?", product));
        create(request("ORDER_SUBMIT_retry", OrderKind.DIRECT, null, null, 1));
        assertEquals(0, value("select available_stock from product where id=?", product));
    }

    @Test void activityEventCouponAndOrderCommitTogether() {
        long activity = jdbc.queryForObject("""
                insert into marketing_activity(name,product_id,sale_price_minor,initial_stock,available_stock,
                    purchase_limit_per_user,starts_at,ends_at,status,created_by,updated_by)
                values (?,?,50,3,3,1,now()-interval '1 hour',now()+interval '1 hour','ACTIVE',?,?) returning id
                """, Long.class, "Sale", product, operator, operator);
        jdbc.update("insert into activity_inventory_sequence(activity_id) values (?)", activity);
        jdbc.update("insert into activity_inventory_checkpoint(activity_id) values (?)", activity);
        long template = jdbc.queryForObject("""
                insert into coupon_template(name,threshold_minor,discount_minor,issue_limit,issued_count,
                    claim_limit_per_user,claim_starts_at,claim_ends_at,use_starts_at,use_ends_at,status,created_by,updated_by)
                values ('Coupon',0,10,3,1,1,now()-interval '1 hour',now()+interval '1 hour',
                    now()-interval '1 hour',now()+interval '1 hour','ACTIVE',?,?) returning id
                """, Long.class, operator, operator);
        long coupon = jdbc.queryForObject("insert into user_coupon(coupon_template_id,user_id) values (?,?) returning id",
                Long.class, template, customer);
        var request = request("ORDER_SUBMIT_activity", OrderKind.ACTIVITY, activity, coupon, 1);
        Order order = create(request);
        assertEquals(40, order.payableAmountMinor());
        assertEquals(1, value("select count(*) from activity_inventory_event where activity_id=?", activity));
        assertEquals(2, value("select count(*) from order_outbox where aggregate_id=? or aggregate_id=?", order.orderNumber(), Long.toString(activity)));
        assertEquals(2, value("select next_event_sequence from activity_inventory_sequence where activity_id=?", activity));
        assertEquals(1, value("select committed_quantity from activity_user_quota where activity_id=? and user_id=?", activity, customer));
        assertEquals(1, value("select count(*) from coupon_reservation where user_coupon_id=?", coupon));
        assertEquals("RESERVED", jdbc.queryForObject("select status::text from user_coupon where id=?", String.class, coupon));
        assertEquals(1, redis.completed.get());
        assertEquals(order, create(request));
        assertEquals(1, redis.reserved.get());
        assertEquals(0, redis.compensated.get());
    }

    @Test void concurrentSameKeyCreatesOneOrder() throws Exception {
        var request = request("ORDER_SUBMIT_parallel", OrderKind.DIRECT, null, null, 2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return create(request); });
            var b = pool.submit(() -> { start.await(); return create(request); });
            start.countDown();
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, value("select count(*) from customer_order where user_id=?", customer));
        assertEquals(1, value("select available_stock from product where id=?", product));
    }

    @Test void multiProductFailureRollsBackEveryReservation() {
        long soldOut = jdbc.queryForObject("""
                insert into product(sku,name,list_price_minor,available_stock,status,created_by,updated_by)
                values (?,?,100,0,'ON_SALE',?,?) returning id
                """, Long.class, "sold-" + UUID.randomUUID(), "Sold out", operator, operator);
        var preview = new OrderPreviewRequest(customer, OrderKind.DIRECT, null, null,
                List.of(new OrderPreviewRequest.Item(product, 2), new OrderPreviewRequest.Item(soldOut, 1)));
        var request = new OrderCreateRequest(customer, "ORDER_SUBMIT_multi", preview);
        assertThrows(IllegalArgumentException.class, () -> create(request));
        assertEquals(3, value("select available_stock from product where id=?", product));
        assertEquals(0, value("select count(*) from customer_order where user_id=?", customer));
        assertEquals(0, value("""
                select count(*) from inventory_reservation r join order_item i on i.id=r.order_item_id
                join customer_order o on o.id=i.order_id where o.user_id=?
                """, customer));
        assertEquals(0, value("select count(*) from order_submission_idempotency where idempotency_key='ORDER_SUBMIT_multi'"));
    }

    @Test void failedCouponLockRollsBackActivitySequenceAndCompensatesRedis() {
        long activity = jdbc.queryForObject("""
                insert into marketing_activity(name,product_id,sale_price_minor,initial_stock,available_stock,
                    purchase_limit_per_user,starts_at,ends_at,status,created_by,updated_by)
                values (?,?,50,3,3,1,now()-interval '1 hour',now()+interval '1 hour','ACTIVE',?,?) returning id
                """, Long.class, "Sale", product, operator, operator);
        jdbc.update("insert into activity_inventory_sequence(activity_id) values (?)", activity);
        var request = request("ORDER_SUBMIT_fail", OrderKind.ACTIVITY, activity, 999999L, 1);
        assertThrows(RuntimeException.class, () -> create(request));
        assertEquals(0, value("select count(*) from customer_order where user_id=?", customer));
        assertEquals(1, value("select next_event_sequence from activity_inventory_sequence where activity_id=?", activity));
        assertEquals(0, value("select count(*) from order_outbox"));
        assertEquals(1, redis.compensated.get());
    }

    private Order create(OrderCreateRequest request) {
        var preview = new OrderPreviewService(id -> new ProductGateway.ProductSnapshot(id, "SKU", "Product", 100, 100, true),
                id -> new ActivityGateway.ActivitySnapshot(id, product, 50, OffsetDateTime.now().minusHours(1),
                        OffsetDateTime.now().plusHours(1), "ACTIVE", 1),
                (user, coupon) -> new CouponGateway.CouponSnapshot(coupon, user, 0, 10, true), Clock.systemUTC());
        return new OrderService(store, preview).create(request);
    }
    private OrderCreateRequest request(String key, OrderKind kind, Long activity, Long coupon, int quantity) {
        return new OrderCreateRequest(customer, key,
                new OrderPreviewRequest(customer, kind, activity, coupon,
                        List.of(new OrderPreviewRequest.Item(product, quantity))));
    }
    private int value(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private static final class FakeActivityRedis implements OrderActivityInventory {
        final AtomicInteger reserved = new AtomicInteger(), completed = new AtomicInteger(), compensated = new AtomicInteger();
        public void reserve(long activity, long user, int quantity, int limit, String key) { reserved.incrementAndGet(); }
        public void complete(long activity, String key) { completed.incrementAndGet(); }
        public void compensate(long activity, long user, int quantity, String key) { compensated.incrementAndGet(); }
    }
}
