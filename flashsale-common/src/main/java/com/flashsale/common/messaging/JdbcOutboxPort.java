package com.flashsale.common.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL adapter for one service-owned Outbox table. */
public final class JdbcOutboxPort implements OutboxPort {
    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String table;
    private final TransactionTemplate transactions;
    private final int maxAttempts;

    public JdbcOutboxPort(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager,
                          String table, int maxAttempts) {
        if (!table.matches("[a-z][a-z0-9_]*_outbox")) throw new IllegalArgumentException("invalid outbox table");
        if (maxAttempts < 1) throw new IllegalArgumentException("max attempts must be positive");
        this.jdbc = jdbc;
        this.json = json;
        this.table = table;
        this.transactions = new TransactionTemplate(manager);
        this.maxAttempts = maxAttempts;
    }

    @Override
    public List<OutboxRecord> claimDue(Instant now, int batchSize, Duration lease) {
        String sql = "WITH claimed AS (SELECT id FROM " + table
                + " WHERE status IN ('PENDING','FAILED') AND attempt_count < ? AND available_at <= ?"
                + " AND (locked_until IS NULL OR locked_until < ?) ORDER BY id"
                + " LIMIT ? FOR UPDATE SKIP LOCKED)"
                + " UPDATE " + table + " o SET locked_until = ?, attempt_count = attempt_count + 1, updated_at = now()"
                + " FROM claimed WHERE o.id = claimed.id"
                + " RETURNING o.id, o.event_id, o.event_type, o.idempotency_key, o.aggregate_type,"
                + " o.aggregate_id, o.payload::text, o.trace_id, o.status, o.attempt_count,"
                + " o.available_at, o.locked_until, o.created_at";
        return transactions.execute(status -> jdbc.query(sql, this::read,
                maxAttempts, Timestamp.from(now), Timestamp.from(now), batchSize, Timestamp.from(now.plus(lease))));
    }

    @Override
    public boolean markSent(OutboxRecord record, Instant sentAt) {
        return jdbc.update("UPDATE " + table + " SET status='SENT', sent_at=?, locked_until=NULL, updated_at=now()"
                        + " WHERE id=? AND status IN ('PENDING','FAILED') AND locked_until=? AND attempt_count=?",
                Timestamp.from(sentAt), record.id(), Timestamp.from(record.lockedUntil()), record.attemptCount()) == 1;
    }

    @Override
    public boolean markFailed(OutboxRecord record, Instant nextAttemptAt, String error) {
        return jdbc.update("UPDATE " + table + " SET status='FAILED', available_at=?, locked_until=NULL, last_error=?, updated_at=now()"
                        + " WHERE id=? AND status IN ('PENDING','FAILED') AND locked_until=? AND attempt_count=?",
                Timestamp.from(nextAttemptAt), error, record.id(), Timestamp.from(record.lockedUntil()), record.attemptCount()) == 1;
    }

    @Override
    public OutboxBacklog backlog(Instant now) {
        return jdbc.queryForObject("SELECT count(*) AS pending, min(created_at) AS oldest FROM " + table
                + " WHERE status IN ('PENDING','FAILED')", (rs, row) -> {
            var oldest = rs.getTimestamp("oldest");
            return new OutboxBacklog(rs.getInt("pending"), oldest == null ? Duration.ZERO
                    : Duration.between(oldest.toInstant(), now).isNegative() ? Duration.ZERO
                    : Duration.between(oldest.toInstant(), now));
        });
    }

    private OutboxRecord read(ResultSet rs, int row) throws SQLException {
        Map<String, Object> payload;
        try {
            payload = json.readValue(rs.getString("payload"), PAYLOAD);
        } catch (Exception error) {
            throw new SQLException("invalid outbox payload", error);
        }
        var message = new MessageEnvelope(rs.getObject("event_id", UUID.class), rs.getString("event_type"), 1,
                rs.getString("idempotency_key"), producer(), rs.getString("aggregate_type"),
                rs.getString("aggregate_id"), rs.getTimestamp("created_at") == null ? Instant.EPOCH
                        : rs.getTimestamp("created_at").toInstant(), rs.getString("trace_id"), payload);
        return new OutboxRecord(rs.getLong("id"), message.eventId(), message.idempotencyKey(), message,
                OutboxStatus.valueOf(rs.getString("status")), rs.getInt("attempt_count"),
                rs.getTimestamp("available_at").toInstant(), rs.getTimestamp("locked_until") == null ? null
                        : rs.getTimestamp("locked_until").toInstant());
    }

    private String producer() { return table.substring(0, table.length() - "_outbox".length()); }
}
