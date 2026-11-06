-- Review-only, additive PostgreSQL migration. NOT executed on production.
-- Existing global ADMIN-owned records remain tenant_id NULL.
-- Global email uniqueness is intentionally unchanged pending product/DB review.
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id uuid;
CREATE INDEX IF NOT EXISTS ix_users_tenant_id_id ON users(tenant_id,id);
COMMIT;
