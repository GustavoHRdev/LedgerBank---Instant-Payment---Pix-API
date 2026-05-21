ALTER TABLE transfer_requests
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN failure_reason VARCHAR(255);

UPDATE transfer_requests
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE transfer_requests
    ALTER COLUMN updated_at DROP DEFAULT;

CREATE TABLE processed_messages (
    id UUID PRIMARY KEY,
    message_id VARCHAR(100) NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_account_processed_messages_message_id UNIQUE (message_id)
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_events_aggregate_id ON audit_events(aggregate_id);
