CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    user_id VARCHAR(80) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_session_id VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS payment_outbox (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    order_id UUID NOT NULL,
    target VARCHAR(20) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_outbox_dedupe
    ON payment_outbox(payment_id, event_type, target);
CREATE INDEX IF NOT EXISTS ix_payment_outbox_status_next
    ON payment_outbox(status, next_attempt_at);
