CREATE TABLE IF NOT EXISTS gateway_payment_dispute_closure (
    id bigserial PRIMARY KEY,
    provider text NOT NULL CHECK (provider IN ('stripe', 'paypal')),
    account text NOT NULL,
    event_id text NOT NULL,
    dispute_id text NOT NULL,
    resolved_at timestamptz NOT NULL,
    status text NOT NULL,
    outcome text,
    UNIQUE (provider, account, event_id)
);

CREATE INDEX IF NOT EXISTS ix_gateway_payment_dispute_closure_feed
    ON gateway_payment_dispute_closure(provider, account, id);
