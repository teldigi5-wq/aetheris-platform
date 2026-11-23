-- B4S read-only comparison on disposable PostgreSQL only.
-- Exclude constraint names but preserve actual physical column types,
-- nullability, generated identity, PK/unique/FK definitions.
SELECT 'COLUMN|' || column_name || '|' || data_type || '|' ||
       coalesce(character_maximum_length::text, '-') || '|' ||
       is_nullable || '|' || is_identity
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'users'
UNION ALL
SELECT 'CONSTRAINT|' || contype::text || '|' || pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = to_regclass('public.users') AND contype IN ('p','u','f')
ORDER BY 1;
