INSERT INTO accounts (id, owner_name, status, currency, created_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Alice Payer', 'ACTIVE', 'BRL', now()),
    ('22222222-2222-2222-2222-222222222222', 'Bob Payee', 'ACTIVE', 'BRL', now());

INSERT INTO pix_keys (id, account_id, value, active, created_at)
VALUES
    ('33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', 'bob@example.com', true, now());

INSERT INTO transfer_requests (
    id, source_account_id, destination_pix_key, amount, currency, status, failure_reason, idempotency_key, created_at, updated_at
)
VALUES (
    '44444444-4444-4444-4444-444444444444',
    '11111111-1111-1111-1111-111111111111',
    'bootstrap',
    1000.0000,
    'BRL',
    'COMPLETED',
    null,
    'bootstrap-seed',
    now(),
    now()
);

INSERT INTO ledger_entries (id, account_id, transfer_id, entry_type, amount, running_balance, created_at)
VALUES (
    '55555555-5555-5555-5555-555555555555',
    '11111111-1111-1111-1111-111111111111',
    '44444444-4444-4444-4444-444444444444',
    'CREDIT',
    1000.0000,
    1000.0000,
    now()
);
