CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    owner_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_accounts_currency CHECK (currency = 'BRL')
);

CREATE TABLE pix_keys (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    value VARCHAR(77) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_pix_keys_value UNIQUE (value)
);

CREATE TABLE transfer_requests (
    id UUID PRIMARY KEY,
    source_account_id UUID NOT NULL REFERENCES accounts(id),
    destination_pix_key VARCHAR(77) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(255),
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_transfer_requests_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED')),
    CONSTRAINT chk_transfer_requests_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transfer_requests_currency CHECK (currency = 'BRL')
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    transfer_id UUID NOT NULL REFERENCES transfer_requests(id),
    entry_type VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    running_balance NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ledger_entries_type CHECK (entry_type IN ('CREDIT', 'DEBIT', 'REVERSAL_CREDIT', 'REVERSAL_DEBIT')),
    CONSTRAINT chk_ledger_entries_amount_positive CHECK (amount > 0)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT chk_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE TABLE processed_messages (
    id UUID PRIMARY KEY,
    message_id VARCHAR(100) NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_processed_messages_message_id UNIQUE (message_id)
);

CREATE INDEX idx_ledger_entries_account_created_at ON ledger_entries(account_id, created_at DESC);
CREATE INDEX idx_outbox_events_status_created_at ON outbox_events(status, created_at);
