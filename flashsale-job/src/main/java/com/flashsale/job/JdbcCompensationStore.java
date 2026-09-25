package com.flashsale.job;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcCompensationStore implements CompensationStore {
    private final JdbcTemplate jdbc;

    public JdbcCompensationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<CompensationRecord> find(String key) {
        return jdbc.query("select issue_key, reason, original_state, target_state, trace_id, success, error, completed_at from compensation_record where issue_key = ?",
                rs -> rs.next() ? Optional.of(new CompensationRecord(rs.getString("issue_key"), rs.getString("reason"),
                        rs.getString("original_state"), rs.getString("target_state"), rs.getString("trace_id"),
                        rs.getBoolean("success"), rs.getString("error"), rs.getTimestamp("completed_at").toInstant())) : Optional.empty(), key);
    }

    @Override
    public void save(CompensationRecord record) {
        jdbc.update("insert into compensation_record(issue_key, reason, original_state, target_state, trace_id, success, error, completed_at, updated_at) values (?,?,?,?,?,?,?,?,now()) "
                        + "on conflict (issue_key) do update set reason=excluded.reason, original_state=excluded.original_state, target_state=excluded.target_state, trace_id=excluded.trace_id, success=excluded.success, error=excluded.error, completed_at=excluded.completed_at, updated_at=now()",
                record.key(), record.reason(), record.originalState(), record.targetState(), record.traceId(), record.success(), record.error(), Timestamp.from(record.completedAt()));
    }
}
