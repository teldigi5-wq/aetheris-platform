-- Additive Flyway migration; audit-service Flyway history remains authoritative.
-- Do not infer tenant ownership from user_id or email: old events stay NULL.
ALTER TABLE audit_events
    ADD COLUMN IF NOT EXISTS tenant_id UUID;
CREATE INDEX IF NOT EXISTS idx_audit_events_tenant_occurred
    ON audit_events (tenant_id, occurred_at DESC)
    WHERE tenant_id IS NOT NULL;
