-- Review-only, additive PostgreSQL migration. NOT executed on production.
-- Historical accounts remain tenant_id NULL: never auto-claim ownership.
BEGIN;
ALTER TABLE identity_accounts ADD COLUMN IF NOT EXISTS tenant_id uuid;
CREATE INDEX IF NOT EXISTS ix_identity_accounts_tenant_id ON identity_accounts(tenant_id);
COMMIT;
