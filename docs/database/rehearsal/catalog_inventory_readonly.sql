-- METADATA ONLY: no SELECT from application tables, no row values.
-- Restricted to three known table names and known column names.
-- Intended for a database role with catalog read permissions; operator controls
-- host, connectivity, snapshot and private output destination.
\set ON_ERROR_STOP on
BEGIN TRANSACTION READ ONLY;
WITH targets AS (
  SELECT r.oid, r.relname
  FROM pg_class r
  JOIN pg_namespace n ON n.oid = r.relnamespace
  WHERE n.nspname = 'public'
    AND r.relkind IN ('r', 'p')
    AND r.relname IN ('identity_accounts','identity_refresh_tokens','users')
)
SELECT jsonb_build_object(
  'contract_version', 1,
  'tables', COALESCE(jsonb_agg(jsonb_build_object(
    'name', r.relname,
    'columns', (
      SELECT COALESCE(jsonb_agg(jsonb_build_object(
        'name', a.attname,
        'type', CASE t.typname
          WHEN 'int8' THEN 'bigint'
          WHEN 'varchar' THEN 'varchar'
          WHEN 'timestamptz' THEN 'timestamptz'
          WHEN 'bool' THEN 'boolean'
          ELSE 'other'
        END,
        'max_length', CASE WHEN t.typname = 'varchar'
                          THEN a.atttypmod - 4 ELSE NULL END,
        'not_null', a.attnotnull,
        'auto_generated', (a.attidentity <> '' OR d.oid IS NOT NULL)
      ) ORDER BY a.attnum), '[]'::jsonb)
      FROM pg_attribute a
      JOIN pg_type t ON t.oid = a.atttypid
      LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
      WHERE a.attrelid = r.oid AND a.attnum > 0 AND NOT a.attisdropped
        AND a.attname IN ('id', 'name', 'email', 'password_hash', 'role', 'account_id', 'token_hash', 'expires_at', 'revoked', 'created_at')
    ),
    'extra_column_count', (
      SELECT count(*) FROM pg_attribute a
      WHERE a.attrelid = r.oid AND a.attnum > 0 AND NOT a.attisdropped
        AND NOT (
              (r.relname = 'identity_accounts' AND a.attname IN ('id', 'name', 'email', 'password_hash', 'role'))
              OR (r.relname = 'identity_refresh_tokens' AND a.attname IN ('id', 'account_id', 'token_hash', 'expires_at', 'revoked', 'created_at'))
              OR (r.relname = 'users' AND a.attname IN ('id', 'name', 'email'))
        )
    ),
    'primary_id', EXISTS (
          SELECT 1 FROM pg_constraint k
          JOIN pg_attribute ca ON ca.attrelid = r.oid
             AND ca.attname = 'id'
          WHERE k.conrelid = r.oid
            AND k.contype = 'p'
            AND array_length(k.conkey, 1) = 1
            AND ca.attnum = ANY(k.conkey)
            
        ),
    'unique_email', EXISTS (
          SELECT 1 FROM pg_constraint k
          JOIN pg_attribute ca ON ca.attrelid = r.oid
             AND ca.attname = 'email'
          WHERE k.conrelid = r.oid
            AND k.contype = 'u'
            AND array_length(k.conkey, 1) = 1
            AND ca.attnum = ANY(k.conkey)
            
        ),
    'unique_token_hash', EXISTS (
          SELECT 1 FROM pg_constraint k
          JOIN pg_attribute ca ON ca.attrelid = r.oid
             AND ca.attname = 'token_hash'
          WHERE k.conrelid = r.oid
            AND k.contype = 'u'
            AND array_length(k.conkey, 1) = 1
            AND ca.attnum = ANY(k.conkey)
            
        ),
    'refresh_account_fk', EXISTS (
          SELECT 1 FROM pg_constraint k
          JOIN pg_attribute ca ON ca.attrelid = r.oid
             AND ca.attname = 'account_id'
          WHERE k.conrelid = r.oid
            AND k.contype = 'f'
            AND array_length(k.conkey, 1) = 1
            AND ca.attnum = ANY(k.conkey)
            AND k.confrelid = to_regclass('public.identity_accounts')
        ),
    'named_token_index', EXISTS (
      SELECT 1 FROM pg_index ix
      JOIN pg_class index_rel ON index_rel.oid = ix.indexrelid
      JOIN pg_attribute indexed_col ON indexed_col.attrelid = r.oid
           AND indexed_col.attname = 'token_hash'
      WHERE ix.indrelid = r.oid
        AND ix.indisunique AND ix.indisvalid AND ix.indisready
        AND ix.indnkeyatts = 1 AND ix.indkey[0] = indexed_col.attnum
        AND ix.indpred IS NULL
        AND index_rel.relname = 'idx_refresh_token_hash'
    )
  ) ORDER BY r.relname), '[]'::jsonb)
)::text FROM targets r;
COMMIT;
