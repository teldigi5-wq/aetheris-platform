-- B4X: operator-controlled read-only summary of Audit Flyway history ROW METADATA.
-- Intended ONLY after B4W confirms the table exists, on approved replica/restore.
-- This emits fixed booleans, not raw checksums, account names, dates, or history rows.
-- It CANNOT verify the checksum against V1 migration SQL; Flyway must do that.
\set ON_ERROR_STOP on
BEGIN TRANSACTION READ ONLY;
SELECT jsonb_build_object(
  'contract_version', 1,
  'exactly_one_history_row', count(*) = 1,
  'exact_v1_metadata', count(*) FILTER (
    WHERE installed_rank = 1
      AND version = '1'
      AND description = 'create audit events'
      AND type = 'SQL'
      AND script = 'V1__create_audit_events.sql'
      AND success IS TRUE
  ) = 1,
  'no_baseline_rows', count(*) FILTER (WHERE type = 'BASELINE') = 0,
  'no_failed_rows', count(*) FILTER (WHERE success IS NOT TRUE) = 0,
  'no_unexpected_rows', count(*) FILTER (
    WHERE (
      installed_rank = 1
      AND version = '1'
      AND description = 'create audit events'
      AND type = 'SQL'
      AND script = 'V1__create_audit_events.sql'
      AND success IS TRUE
    ) IS NOT TRUE
  ) = 0,
  'checksum_present', count(*) FILTER (
    WHERE checksum IS NOT NULL
  ) = 1
)::text FROM public.flyway_audit_schema_history;
COMMIT;
