CREATE TABLE compensation_record (
    issue_key VARCHAR(255) PRIMARY KEY,
    reason VARCHAR(255) NOT NULL,
    original_state VARCHAR(100) NOT NULL,
    target_state VARCHAR(100) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    success BOOLEAN NOT NULL,
    error TEXT,
    completed_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX compensation_record_failed_idx ON compensation_record (updated_at) WHERE success = false;
