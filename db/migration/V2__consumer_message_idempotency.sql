ALTER TABLE order_message_idempotency ADD COLUMN message_id VARCHAR(128);
ALTER TABLE coupon_message_idempotency ADD COLUMN message_id VARCHAR(128);
ALTER TABLE inventory_message_idempotency ADD COLUMN message_id VARCHAR(128);
ALTER TABLE payment_message_idempotency ADD COLUMN message_id VARCHAR(128);

CREATE TABLE activity_message_idempotency (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    event_id UUID NOT NULL,
    message_id VARCHAR(128),
    event_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    status message_consumer_status NOT NULL DEFAULT 'PROCESSING',
    trace_id VARCHAR(128),
    attempt_count INTEGER NOT NULL DEFAULT 1 CHECK (attempt_count > 0),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX activity_message_idempotency_recovery_idx
    ON activity_message_idempotency (started_at) WHERE status = 'PROCESSING';
