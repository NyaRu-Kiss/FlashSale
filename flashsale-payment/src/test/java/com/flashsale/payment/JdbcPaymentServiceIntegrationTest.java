package com.flashsale.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Run with FLASHSALE_TEST_JDBC_URL against a temporary PostgreSQL 16 initialized from V1. */
class JdbcPaymentServiceIntegrationTest {
    private JdbcTemplate jdbc;
    private long customer;
    private long operator;
    private long order;
    private long product;
    private JdbcPaymentService service;

    @BeforeEach void setup() {
        String url = System.getenv("FLASHSALE_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank());
        var dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("FLASHSALE_TEST_JDBC_USER", "postgres"),
                System.getenv().getOrDefault("FLASHSALE_TEST_JDBC_PASSWORD", "postgres"));
        jdbc = new JdbcTemplate(dataSource);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        operator = jdbc.queryForObject("insert into app_user(username,password_hash,role) values (?,?,'OPERATOR') returning id", Long.class, "pay-op-" + suffix, "hash");
        customer = jdbc.queryForObject("insert into app_user(username,password_hash) values (?,?) returning id", Long.class, "pay-customer-" + suffix, "hash");
        product = jdbc.queryForObject("""
                insert into product(sku,name,list_price_minor,available_stock,status,created_by,updated_by)
                values (?, 'Payment product', 100, 0, 'ON_SALE', ?, ?) returning id
                """, Long.class, "pay-sku-" + suffix, operator, operator);
        var transactionManager = new DataSourceTransactionManager(dataSource);
        AtomicLong orderId = new AtomicLong();
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            orderId.set(jdbc.queryForObject("""
                    insert into customer_order(order_number,user_id,kind,status,item_subtotal_minor,payable_amount_minor,expires_at)
                    values (?,?,'DIRECT','PENDING_PAYMENT',100,100,now()+interval '15 minutes') returning id
                    """, Long.class, "PAY-" + suffix, customer));
            jdbc.update("""
                    insert into order_item(order_id,product_id,quantity,sku_snapshot,product_name_snapshot,
                        list_price_minor,sale_price_minor,activity_discount_minor,line_amount_minor)
                    values (?, ?, 1, 'pay-sku', 'Payment product', 100, 100, 0, 100)
                    """, orderId.get(), product);
            return null;
        });
        order = orderId.get();
        service = new JdbcPaymentService(jdbc, transactionManager, new SimulatedPaymentGateway(), new ObjectMapper(), Clock.fixed(OffsetDateTime.now(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
    }

    @Test void paymentIsIdempotentAndConfirmsOrder履约AndOutbox() {
        String number = jdbc.queryForObject("select order_number from customer_order where id=?", String.class, order);
        var first = service.pay(customer, number, "PAYMENT_KEY", 100, "CNY", "SUCCESS");
        var retry = service.pay(customer, number, "PAYMENT_KEY", 100, "CNY", "SUCCESS");
        var callback = new PaymentGateway.CallbackRequest(first.transactionId(), 100, "CNY", "SUCCESS", "callback-event-" + UUID.randomUUID());
        assertTrue(service.callback(callback).success());
        assertTrue(service.callback(callback).success());
        assertTrue(first.success());
        assertEquals(first, retry);
        assertEquals("COMPLETED", jdbc.queryForObject("select status::text from customer_order where id=?", String.class, order));
        assertEquals(1, jdbc.queryForObject("select count(*) from payment_record where order_id=?", Integer.class, order));
        assertEquals(1, jdbc.queryForObject("select count(*) from fulfillment_record where order_id=?", Integer.class, order));
        assertEquals(2, jdbc.queryForObject("select count(*) from payment_outbox where aggregate_id=?", Integer.class, Long.toString(order)));
    }

    @Test void mismatchedAmountIsRejectedBeforePaymentRecord() {
        String number = jdbc.queryForObject("select order_number from customer_order where id=?", String.class, order);
        var result = service.pay(customer, number, "PAYMENT_BAD_AMOUNT", 99, "CNY", "SUCCESS");
        assertFalse(result.success());
        assertEquals("PAYMENT_AMOUNT_MISMATCH", result.failureCode());
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_record where order_id=?", Integer.class, order));
        assertEquals("PENDING_PAYMENT", jdbc.queryForObject("select status::text from customer_order where id=?", String.class, order));
    }
}
