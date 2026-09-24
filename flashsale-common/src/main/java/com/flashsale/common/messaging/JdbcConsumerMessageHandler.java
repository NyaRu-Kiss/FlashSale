package com.flashsale.common.messaging;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Persists the claim before work, then commits domain writes and SUCCEEDED together. */
public final class JdbcConsumerMessageHandler {
    private static final Set<String> TABLES = Set.of("order_message_idempotency", "coupon_message_idempotency",
            "inventory_message_idempotency", "payment_message_idempotency", "activity_message_idempotency");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final TransactionTemplate claimTransaction;
    private final String table;
    private final Duration timeout;
    private final ConsumerMessageHandler.BusinessHandler business;

    public JdbcConsumerMessageHandler(JdbcTemplate jdbc, PlatformTransactionManager manager, String table,
                                      Duration timeout, ConsumerMessageHandler.BusinessHandler business) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("INVALID_CONSUMER_TABLE");
        if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("INVALID_CONSUMER_TIMEOUT");
        this.jdbc = jdbc;
        this.table = table;
        this.timeout = timeout;
        this.business = business;
        this.transaction = new TransactionTemplate(manager);
        this.claimTransaction = new TransactionTemplate(manager);
        this.claimTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ConsumerMessageHandler.HandleResult handle(MessageEnvelope message, String messageId) {
        if (message == null || messageId == null || messageId.isBlank()) throw new IllegalArgumentException("INVALID_CONSUMER_MESSAGE");
        Claim claim = claimTransaction.execute(status -> claim(message, messageId));
        if (claim == Claim.SUCCEEDED) return ConsumerMessageHandler.HandleResult.DUPLICATE;
        if (claim == Claim.BUSY) return ConsumerMessageHandler.HandleResult.RETRY;
        try {
            transaction.executeWithoutResult(status -> {
                String current = jdbc.queryForObject("select status::text from " + table
                        + " where idempotency_key=? and event_id=? for update", String.class,
                        message.idempotencyKey(), message.eventId());
                if (!"PROCESSING".equals(current)) throw new IllegalStateException("CONSUMER_CLAIM_LOST");
                try { business.process(message); }
                catch (Exception error) { throw new BusinessFailure(error); }
                if (jdbc.update("update " + table + " set status='SUCCEEDED',completed_at=now(),updated_at=now(),last_error=null"
                        + " where idempotency_key=? and event_id=? and status='PROCESSING'",
                        message.idempotencyKey(), message.eventId()) != 1) throw new IllegalStateException("CONSUMER_CLAIM_LOST");
            });
            return ConsumerMessageHandler.HandleResult.SUCCEEDED;
        } catch (Exception error) {
            claimTransaction.executeWithoutResult(status -> jdbc.update("update " + table
                    + " set status='FAILED',completed_at=now(),updated_at=now(),last_error=?"
                    + " where idempotency_key=? and event_id=? and status='PROCESSING'",
                    error.toString(), message.idempotencyKey(), message.eventId()));
            return ConsumerMessageHandler.HandleResult.RETRY;
        }
    }

    private Claim claim(MessageEnvelope message, String messageId) {
        int inserted = jdbc.update("insert into " + table
                + "(idempotency_key,event_id,event_type,aggregate_id,trace_id,message_id)"
                + " values (?,?,?,?,?,?) on conflict (idempotency_key) do nothing",
                message.idempotencyKey(), message.eventId(), message.eventType(), message.aggregateId(), message.traceId(), messageId);
        if (inserted == 1) return Claim.STARTED;
        UUID existing = jdbc.queryForObject("select event_id from " + table + " where idempotency_key=?", UUID.class, message.idempotencyKey());
        if (!message.eventId().equals(existing)) throw new IllegalArgumentException("CONSUMER_IDEMPOTENCY_CONFLICT");
        int reclaimed = jdbc.update("update " + table
                + " set status='PROCESSING',attempt_count=attempt_count+1,started_at=now(),completed_at=null,"
                + " last_error=null,message_id=?,updated_at=now()"
                + " where idempotency_key=? and event_id=? and (status='FAILED'"
                + " or (status='PROCESSING' and started_at < now() - (? * interval '1 millisecond')))",
                messageId, message.idempotencyKey(), message.eventId(), timeout.toMillis());
        if (reclaimed == 1) return Claim.STARTED;
        String state = jdbc.queryForObject("select status::text from " + table + " where idempotency_key=?", String.class,
                message.idempotencyKey());
        return "SUCCEEDED".equals(state) ? Claim.SUCCEEDED : Claim.BUSY;
    }

    private enum Claim { STARTED, SUCCEEDED, BUSY }
    private static final class BusinessFailure extends RuntimeException {
        private BusinessFailure(Exception cause) { super(cause); }
    }
}
