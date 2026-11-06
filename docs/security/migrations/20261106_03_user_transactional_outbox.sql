-- MANUAL REVIEW-ONLY additive schema. Do NOT run on production automatically.
-- Requires PostgreSQL 16. Uses the SAME database/transaction as users.
-- Historical events are NOT reconstructed from existing rows.
BEGIN;
CREATE TABLE IF NOT EXISTS user_event_outbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL CHECK (event_type IN ('user.created','user.deleted')),
    occurred_at TIMESTAMPTZ NOT NULL,
    user_id BIGINT NOT NULL,
    name TEXT NOT NULL,
    email TEXT NOT NULL,
    tenant_id UUID NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'pending'
      CHECK (state IN ('pending','retry','leased','delivered','parked')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    lease_token UUID NULL,
    lease_until TIMESTAMPTZ NULL,
    delivered_at TIMESTAMPTZ NULL,
    CONSTRAINT user_outbox_lease_shape CHECK (
      (state='leased' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
      OR (state <> 'leased' AND lease_token IS NULL AND lease_until IS NULL)
    )
);
CREATE INDEX IF NOT EXISTS ix_user_event_outbox_ready
    ON user_event_outbox (available_at, event_id)
    WHERE state IN ('pending','retry','leased');
COMMIT;
