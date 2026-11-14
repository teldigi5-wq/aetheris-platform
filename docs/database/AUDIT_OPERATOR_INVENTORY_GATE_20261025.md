# B4V — Audit operator metadata inventory gate (draft only)

**25 October 2026. This is an operator-triage research tool, NOT a migration, release approval, or startup bypass.** Stacked on [platform PR #278](https://github.com/teldigi5-wq/aetheris-platform/pull/278) without merging any earlier draft PR to `main`.

## Why this exists

Audit's shipped Flyway default is changed to `baseline-on-migrate: false` only in draft #278. It deliberately fails on an unreviewed populated schema with no Audit history. Before promoting that change, real database **ownership and history must be inspected privately by the responsible operator**, including representative backup/recovery. The fresh disposable success in #277/#278 cannot certify an existing shared database.

Prior draft [#246](https://github.com/teldigi5-wq/aetheris-platform/pull/246) already added a separate **Identity/User column catalog inventory**; this new narrow query focuses only on **Audit table shape, index, history-table existence and neighboring Identity/User table presence**. Neither tool reads application rows.

## Safe offline procedure — owner-operator action only

1. Obtain DBA authorization, identify exact database/schema/role, and use a **read-only role on an approved isolated restored backup or read replica**. Do not ask CI, an AI agent or a general developer account to open production networking.
2. With your own privately configured `psql`, run `psql -X -qAt -v ON_ERROR_STOP=1 -f docs/database/rehearsal/audit_ownership_inventory_readonly.sql > /private/path/audit-metadata.json`. The SQL opens a **READ ONLY** transaction and reads fixed `pg_catalog` metadata only; it does not query customer rows or any Flyway history rows.
3. Run `python3 scripts/audit_ownership_preflight.py /private/path/audit-metadata.json` locally. **Every outcome exits nonzero** and prints only a fixed diagnostic token. Do not commit or upload the real inventory, credentials, SQL logs or database backups.
4. Review the actual, private `flyway_audit_schema_history` entries with the operator separately: migrations, installed ranks, checksums, success, baseline rows, and schema ownership must be reconciled against exact shipped SQL and current deployment history. Existence of the history *table* is deliberately insufficient.
5. Before any promotion, obtain documented approved migration plan (including Identity/User Hibernate `ddl-auto:update` overrides), staged tests against a **representative restored historic database**, verified backup-and-restore, rollback/restart window, and owner authorization. Never invent an automatic `baseline`, suppress Flyway errors or bypass checks.

## Safe outcomes

- `AUDIT_INVENTORY_REVIEW_FRESH_DATABASE_AND_SERVICE_OWNERSHIP` — metadata appears empty, but an operator must still verify actual empty state and ownership.
- `AUDIT_INVENTORY_NO_GO_UNREVIEWED_SHARED_SCHEMA` — Identity/User table exists and no Audit history/table is found; no auto baseline.
- `AUDIT_INVENTORY_NO_GO_AUDIT_HISTORY_ABSENT` — Audit table exists without its own history.
- `AUDIT_INVENTORY_NO_GO_ORPHAN_AUDIT_HISTORY` — history table exists without the Audit table.
- `AUDIT_INVENTORY_NO_GO_AUDIT_SCHEMA_DRIFT` — Audit table does not match the restricted V1 shape, PK or occurred-at index indicators.
- `AUDIT_INVENTORY_REVIEW_HISTORY_CONTENT_AND_OWNERSHIP` — both Audit table and history table exist; actual history **rows, checksums and ownership remain unverified**.
- `AUDIT_INVENTORY_INVALID_INPUT` — malformed/unknown inventory data (exit 2).

The limited metadata query is not a full definition comparator for indexes, grants, triggers, row-level security, extensions, multiple schemas, history checksums or data; the application itself and DBA must independently verify those. No output is a `GO` or production permission.

The GitHub Actions workflow tests Python diagnostics and executes the catalog query against a **network-none, tmpfs PostgreSQL CI container** with entirely synthetic tables. It must not contact real databases. **Issue #242, Stage45 NO_GO, nine unapproved release gates and all draft PRs remain open.**
