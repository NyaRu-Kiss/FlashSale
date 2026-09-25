package com.flashsale.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.trace.TraceContext;
import com.flashsale.common.metrics.BusinessMetrics;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL payment boundary: idempotency, confirmation and the payment outbox share one transaction. */
public final class JdbcPaymentService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PaymentGateway gateway;
    private final ObjectMapper json;
    private final Clock clock;
    private final BusinessMetrics metrics;

    public JdbcPaymentService(JdbcTemplate jdbc, PlatformTransactionManager manager,
                              PaymentGateway gateway, ObjectMapper json, Clock clock) {
        this(jdbc, manager, gateway, json, clock, null);
    }

    public JdbcPaymentService(JdbcTemplate jdbc, PlatformTransactionManager manager,
                              PaymentGateway gateway, ObjectMapper json, Clock clock, BusinessMetrics metrics) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.gateway = gateway;
        this.json = json;
        this.clock = clock;
        this.metrics = metrics;
    }

    public Result pay(long userId, String orderNumber, String idempotencyKey,
                      long amountMinor, String currency, String paymentStatus) {
        try {
            Result result = transactions.execute(status -> payInTransaction(
                    userId, orderNumber, idempotencyKey, amountMinor, currency, paymentStatus));
            if (metrics != null) metrics.payment("pay", result.success() ? "SUCCESS" : result.failureCode());
            return result;
        } catch (RuntimeException error) {
            if (metrics != null) metrics.payment("pay", error.getMessage() == null ? "ERROR" : error.getMessage());
            throw error;
        }
    }

    public Result callback(PaymentGateway.CallbackRequest request) {
        Result result = transactions.execute(status -> callbackInTransaction(request));
        if (metrics != null) metrics.payment("callback", result.success() ? "SUCCESS" : result.failureCode());
        return result;
    }

    private Result payInTransaction(long userId, String orderNumber, String key,
                                    long amountMinor, String currency, String paymentStatus) {
        long orderId = jdbc.queryForObject(
                "select id from customer_order where order_number=? and user_id=?",
                Long.class, orderNumber, userId);
        String fingerprint = fingerprint(orderNumber, amountMinor, currency, paymentStatus);
        Claim claim = claim(userId, orderId, key, fingerprint);
        if (claim.replayed()) return claim.result();

        OrderSnapshot order = order(orderId);
        if (order.amountMinor() != amountMinor || !order.currency().equals(currency)) {
            return reject(claim, "PAYMENT_AMOUNT_MISMATCH");
        }
        if (!"PENDING_PAYMENT".equals(order.status()) || !order.expiresAt().isAfter(OffsetDateTime.now(clock))) {
            return reject(claim, "ORDER_NOT_PAYABLE");
        }
        long paymentId = jdbc.queryForObject(
                "insert into payment_record(order_id,amount_minor,currency) values (?,?,?) returning id",
                Long.class, orderId, amountMinor, currency);
        PaymentGateway.PaymentResult gatewayResult = gateway.initiate(
                new PaymentGateway.PaymentRequest(orderNumber, amountMinor, currency, paymentStatus));
        if (!gatewayResult.success()) {
            jdbc.update("update payment_record set status='FAILED',failure_code=?,failed_at=current_timestamp where id=?",
                    gatewayResult.failureCode(), paymentId);
            return reject(claim, gatewayResult.failureCode());
        }
        jdbc.update("update payment_record set status='SUCCEEDED',provider_transaction_id=?,paid_at=current_timestamp where id=?",
                gatewayResult.transactionId(), paymentId);
        completeOrder(orderId, paymentId, gatewayResult.transactionId(), amountMinor, currency);
        succeed(claim, paymentId);
        return new Result(true, gatewayResult.transactionId(), null);
    }

    private Result callbackInTransaction(PaymentGateway.CallbackRequest request) {
        PaymentGateway.PaymentCallback callback = gateway.verifyAndParse(request);
        String callbackKey = "PAYMENT_CALLBACK:" + callback.eventId();
        if (jdbc.queryForObject("select count(*) from payment_outbox where idempotency_key=?", Integer.class, callbackKey) > 0) {
            return new Result(callback.success(), callback.transactionId(), callback.success() ? null : "PAYMENT_FAILED");
        }
        var paymentIds = jdbc.query("select id from payment_record where provider_transaction_id=?", (rs, row) -> rs.getLong(1), callback.transactionId());
        if (paymentIds.isEmpty()) throw new IllegalArgumentException("PAYMENT_NOT_FOUND");
        Long paymentId = paymentIds.getFirst();
        PaymentSnapshot payment = payment(paymentId);
        if (payment.amountMinor() != callback.amountMinor() || !payment.currency().equals(callback.currency())) {
            throw new IllegalArgumentException("PAYMENT_AMOUNT_MISMATCH");
        }
        if (!callback.success()) {
            jdbc.update("update payment_record set status='FAILED',failure_code='PAYMENT_FAILED',failed_at=current_timestamp where id=? and status='PENDING'", paymentId);
            return new Result(false, callback.transactionId(), "PAYMENT_FAILED");
        }
        jdbc.update("update payment_record set status='SUCCEEDED',paid_at=coalesce(paid_at,current_timestamp) where id=?", paymentId);
        completeOrder(payment.orderId(), paymentId, callback.transactionId(), payment.amountMinor(), payment.currency());
        succeedPaymentIdempotency(payment.orderId(), paymentId);
        outbox(UUID.randomUUID(), "PAYMENT_CALLBACK", callbackKey, payment.orderId(),
                Map.of("event_id", callback.eventId(), "transaction_id", callback.transactionId(),
                        "event_type", "PAYMENT_CALLBACK", "trace_id", TraceContext.getOrCreate()));
        return new Result(true, callback.transactionId(), null);
    }

    private void completeOrder(long orderId, long paymentId, String transactionId, long amountMinor, String currency) {
        int updated = jdbc.update("update customer_order set status='PAID',paid_at=coalesce(paid_at,current_timestamp) where id=? and status='PENDING_PAYMENT'", orderId);
        if (updated == 0) {
            String status = jdbc.queryForObject("select status::text from customer_order where id=?", String.class, orderId);
            if (!"PAID".equals(status) && !"COMPLETED".equals(status)) throw new IllegalArgumentException("ORDER_NOT_PAYABLE");
        }
        jdbc.update("update inventory_reservation set status='CONFIRMED',confirmed_at=current_timestamp where order_item_id in (select id from order_item where order_id=?) and status='RESERVED'", orderId);
        jdbc.update("update coupon_reservation set status='CONFIRMED',consumed_at=current_timestamp where order_id=? and status='RESERVED'", orderId);
        jdbc.update("update user_coupon set status='CONSUMED',consumed_at=current_timestamp,reserved_at=null,version=version+1 where id in (select user_coupon_id from coupon_reservation where order_id=? and status='CONFIRMED') and status='RESERVED'", orderId);
        jdbc.update("insert into fulfillment_record(order_id) values (?) on conflict (order_id) do nothing", orderId);
        jdbc.update("update customer_order set status='COMPLETED',updated_at=current_timestamp where id=? and status='PAID'", orderId);
        UUID eventId = UUID.randomUUID();
        outbox(eventId, "PAYMENT_SUCCEEDED", "PAYMENT_SUCCEEDED:" + paymentId, orderId,
                Map.of("event_id", eventId.toString(), "event_type", "PAYMENT_SUCCEEDED", "payment_record_id", paymentId,
                        "order_id", orderId, "transaction_id", transactionId, "amount_minor", amountMinor,
                        "currency", currency, "trace_id", TraceContext.getOrCreate()));
    }

    private Claim claim(long userId, long orderId, String key, String fingerprint) {
        jdbc.update("insert into payment_submission_idempotency(user_id,order_id,idempotency_key,request_fingerprint) values (?,?,?,?) on conflict (user_id,order_id,idempotency_key) do nothing",
                userId, orderId, key, fingerprint);
        return jdbc.queryForObject("select id,idempotency_key,status::text,request_fingerprint,payment_record_id,response_code from payment_submission_idempotency where user_id=? and order_id=? and idempotency_key=?",
                (rs, row) -> {
                    long id = rs.getLong(1);
                    String claimKey = rs.getString(2);
                    String status = rs.getString(3);
                    if (!fingerprint.equals(rs.getString(4))) throw new IllegalArgumentException("IDEMPOTENCY_CONFLICT");
                    Long paymentId = rs.getObject(5, Long.class);
                    String code = rs.getString(6);
                    if ("PROCESSING".equals(status) && paymentId == null) return new Claim(id, claimKey, false, new Result(false, null, "PAYMENT_PROCESSING"));
                    String tx = paymentId == null ? null : jdbc.queryForObject("select provider_transaction_id from payment_record where id=?", String.class, paymentId);
                    return new Claim(id, claimKey, true, new Result("SUCCEEDED".equals(status), tx, code));
                }, userId, orderId, key);
    }

    private Result reject(Claim claim, String code) {
        jdbc.update("update payment_submission_idempotency set status='REJECTED',response_code=?,completed_at=current_timestamp where id=? and status='PROCESSING'", code, claim.id());
        return new Result(false, null, code);
    }

    private void succeed(Claim claim, long paymentId) {
        jdbc.update("update payment_submission_idempotency set status='SUCCEEDED',payment_record_id=?,response_code=null,completed_at=current_timestamp where id=?", paymentId, claim.id());
    }

    private void succeedPaymentIdempotency(long orderId, long paymentId) {
        jdbc.update("update payment_submission_idempotency set status='SUCCEEDED',payment_record_id=?,response_code=null,completed_at=current_timestamp where order_id=? and status='PROCESSING'", paymentId, orderId);
    }

    private void outbox(UUID eventId, String type, String key, long orderId, Map<String, ?> payload) {
        try {
            jdbc.update("insert into payment_outbox(event_id,event_type,idempotency_key,aggregate_type,aggregate_id,payload,trace_id) values (?,?,?,'PAYMENT',?,?::jsonb,?) on conflict (idempotency_key) do nothing",
                    eventId, type, key, Long.toString(orderId), json.writeValueAsString(payload), TraceContext.getOrCreate());
        } catch (JsonProcessingException e) { throw new IllegalStateException("OUTBOX_SERIALIZATION_FAILED", e); }
    }

    private OrderSnapshot order(long id) { return jdbc.queryForObject("select payable_amount_minor,currency,status::text,expires_at from customer_order where id=?", (rs, row) -> new OrderSnapshot(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class)), id); }
    private PaymentSnapshot payment(long id) { return jdbc.queryForObject("select order_id,amount_minor,currency from payment_record where id=?", (rs, row) -> new PaymentSnapshot(rs.getLong(1), rs.getLong(2), rs.getString(3)), id); }
    private static String fingerprint(String order, long amount, String currency, String status) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (order + "|" + amount + "|" + currency + "|" + status).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE", e);
        }
    }
    private record OrderSnapshot(long amountMinor, String currency, String status, OffsetDateTime expiresAt) {}
    private record PaymentSnapshot(long orderId, long amountMinor, String currency) {}
    private record Claim(long id, String key, boolean replayed, Result result) {}
    public record Result(boolean success, String transactionId, String failureCode) {}
}
