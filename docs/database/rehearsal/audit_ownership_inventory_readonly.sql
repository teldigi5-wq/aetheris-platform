-- B4W operator-controlled METADATA ONLY report (contract v2). Does not inspect user rows or
-- Flyway history ROWS. A history table's existence never proves a valid baseline.
-- Run only on an operator-approved read replica/snapshot with a read-only role.
\set ON_ERROR_STOP on
BEGIN TRANSACTION READ ONLY;
WITH audit_columns AS (
  SELECT a.attname, a.atttypmod, a.attnotnull, t.typname
    FROM pg_catalog.pg_attribute a
    JOIN pg_catalog.pg_type t ON t.oid = a.atttypid
   WHERE a.attrelid = to_regclass('public.audit_events')
     AND a.attnum > 0 AND NOT a.attisdropped
), shape AS (
  SELECT count(*) AS total,
         count(*) FILTER (WHERE
            (attname = 'event_id' AND typname = 'varchar' AND atttypmod = 132 AND attnotnull)
         OR (attname = 'event_type' AND typname = 'varchar' AND atttypmod = 132 AND attnotnull)
         OR (attname = 'occurred_at' AND typname = 'timestamptz' AND attnotnull)
         OR (attname = 'user_id' AND typname = 'int8' AND attnotnull)
         OR (attname = 'name' AND typname = 'text' AND NOT attnotnull)
         OR (attname = 'email' AND typname = 'text' AND NOT attnotnull)
         OR (attname = 'recorded_at' AND typname = 'timestamptz' AND attnotnull)
         ) AS correct
    FROM audit_columns
)
SELECT jsonb_build_object(
  'contract_version', 2,
  'audit_events_exists', to_regclass('public.audit_events') IS NOT NULL,
  'audit_history_exists', to_regclass('public.flyway_audit_schema_history') IS NOT NULL,
  'identity_accounts_exists', to_regclass('public.identity_accounts') IS NOT NULL,
  'identity_refresh_tokens_exists', to_regclass('public.identity_refresh_tokens') IS NOT NULL,
  'users_exists', to_regclass('public.users') IS NOT NULL,
  'audit_column_shape_ok', (shape.total = 7 AND shape.correct = 7),
  'audit_recorded_at_default_ok', EXISTS (
      SELECT 1 FROM pg_catalog.pg_attribute a
      JOIN pg_catalog.pg_attrdef d
        ON d.adrelid = a.attrelid AND d.adnum = a.attnum
      WHERE a.attrelid = to_regclass('public.audit_events')
        AND a.attname = 'recorded_at'
        AND lower(btrim(pg_catalog.pg_get_expr(d.adbin, d.adrelid))) = 'current_timestamp'
  ),
  'audit_primary_key_ok', EXISTS (
      SELECT 1 FROM pg_catalog.pg_constraint c
      JOIN pg_catalog.pg_attribute a ON a.attrelid = c.conrelid
      WHERE c.conrelid = to_regclass('public.audit_events')
        AND c.contype = 'p' AND a.attname = 'event_id'
        AND c.conkey = ARRAY[a.attnum]::smallint[]
  ),
  'audit_occurred_at_index_ok', EXISTS (
      SELECT 1 FROM pg_catalog.pg_index ix
      JOIN pg_catalog.pg_class idx ON idx.oid = ix.indexrelid
      JOIN pg_catalog.pg_am am ON am.oid = idx.relam
      JOIN pg_catalog.pg_attribute a ON a.attrelid = ix.indrelid
      WHERE ix.indrelid = to_regclass('public.audit_events')
        AND idx.relname = 'idx_audit_events_occurred_at'
        AND a.attname = 'occurred_at'
        AND ix.indisvalid AND ix.indisready
        AND ix.indnkeyatts = 1 AND ix.indnatts = 1
        AND ix.indkey[0] = a.attnum
        AND idx.relkind = 'i' AND am.amname = 'btree'
        AND ix.indisunique IS FALSE
        AND ix.indexprs IS NULL AND ix.indpred IS NULL
        -- PostgreSQL B-tree indoption bits: DESC = 1, NULLS FIRST = 2.
        -- Audit V1 ships "occurred_at DESC" (default NULLS FIRST).
        AND ((ix.indoption[0] & 3) = 3)
  )
)::text FROM shape;
COMMIT;
