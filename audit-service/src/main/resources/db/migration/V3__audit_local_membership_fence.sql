-- Additive, disabled-by-default local authorization mirror for Audit ingestion.
-- A row is NEVER self-created from an event. An independently authorized
-- reconciler/operator must install and revoke rows; there is NO event-writable
-- API and no implicit ACTIVE default.
-- This table is not an independent anti-rollback authority.
CREATE TABLE IF NOT EXISTS audit_user_tenant_membership_fence (
    tenant_id UUID NOT NULL,
    user_id BIGINT NOT NULL CHECK (user_id > 0),
    status VARCHAR(12) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    valid_since TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    PRIMARY KEY (tenant_id, user_id)
);
COMMENT ON TABLE audit_user_tenant_membership_fence IS
'Opt-in local Audit transaction fence. Must be reconciled from separately trusted authority; not a membership source of truth.';
