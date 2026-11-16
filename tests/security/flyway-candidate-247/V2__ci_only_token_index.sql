-- EXPERIMENT ONLY: do not move into src/main/resources/db/migration.
-- Synthetic-only CI guarded delta: add explicit refresh-token unique index.
-- The SQL is NOT a Flyway production migration and MUST NOT touch real volumes.
DO $$
BEGIN
 IF to_regclass('public.aetheris_schema_rehearsal_guard') IS NULL THEN
   RAISE EXCEPTION 'CI synthetic rehearsal guard missing; refusing migration';
 END IF;
 IF NOT EXISTS (
     SELECT 1 FROM public.aetheris_schema_rehearsal_guard
       WHERE marker = 'CI_ONLY_SYNTHETIC'
   ) THEN
   RAISE EXCEPTION 'CI synthetic rehearsal marker invalid';
 END IF;
 IF to_regclass('public.identity_accounts') IS NULL
    OR to_regclass('public.identity_refresh_tokens') IS NULL
    OR to_regclass('public.users') IS NULL THEN
   RAISE EXCEPTION 'Unexpected legacy schema: required tables missing';
 END IF;
 IF NOT EXISTS (
   SELECT 1 FROM information_schema.columns
   WHERE table_schema = 'public' AND table_name = 'identity_refresh_tokens'
     AND column_name = 'token_hash' AND data_type = 'character varying'
     AND character_maximum_length = 64 AND is_nullable = 'NO'
 ) THEN
   RAISE EXCEPTION 'Unexpected legacy schema: refresh token hash type drift';
 END IF;
 IF NOT EXISTS (
   SELECT 1 FROM information_schema.columns
   WHERE table_schema = 'public' AND table_name = 'users'
     AND column_name = 'email' AND data_type = 'character varying'
     AND character_maximum_length = 255 AND is_nullable = 'NO'
 ) THEN
   RAISE EXCEPTION 'Unexpected legacy schema: user email type drift';
 END IF;
 IF NOT EXISTS (
   SELECT 1 FROM pg_constraint c
   JOIN pg_class t ON t.oid = c.conrelid
   JOIN pg_namespace n ON n.oid = t.relnamespace
   WHERE n.nspname = 'public' AND t.relname = 'identity_refresh_tokens'
     AND c.contype = 'f' AND c.confrelid = 'public.identity_accounts'::regclass
 ) THEN
   RAISE EXCEPTION 'Unexpected legacy schema: refresh token FK missing';
 END IF;
END
$$;
-- This is a disposable CI demonstration, not a production indexing strategy.
-- Production would need migration locking, write-availability analysis and
-- service-owned Flyway history tables before index construction.
CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_token_hash
    ON identity_refresh_tokens (token_hash);
