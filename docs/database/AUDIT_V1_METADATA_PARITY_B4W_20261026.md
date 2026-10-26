# B4W — tighten Audit V1 catalog metadata parity (review-only draft)

**26 October 2026. Non-deploying, draft and stacked after #279. No persistent database or production migration was run.**

## Scope and observed gap

Previous [PR #279](https://github.com/teldigi5-wq/aetheris-platform/pull/279) provided catalog-only, read-only operator inventory and a triage checker which can **never authorize deployment**. It checked Audit V1 table columns, PK, and whether the required named occurred-at index pointed to the right field. That abbreviated predicate could still report a compatibility-review state after two forms of structural drift:

1. `audit_events.recorded_at` missing `DEFAULT CURRENT_TIMESTAMP`.
2. Index `idx_audit_events_occurred_at` covering the right column but sorted `ASC` rather than the source migration's `DESC`.

## Changes

- **Bump the catalog JSON contract from v1 to v2** in both the SQL query and offline Python preflight. Old v1 JSON is rejected (exit 2), never accidentally interpreted with new semantics. Use matched script/query revisions.
- Inspect the **default expression** for `recorded_at` via catalog `pg_attrdef`, accepting only the normalized `CURRENT_TIMESTAMP` representation from the repository's real V1 SQL.
- Check the index's B-tree access method, single key and no included columns, ordinary index kind, non-unique, no expressions or partial predicate, and `DESC NULLS FIRST` (`pg_index.indoption[0]` bits 1 and 2).
- The fail-closed offline comparator treats either false indicator on an existing Audit table as `NO_GO_AUDIT_SCHEMA_DRIFT`.
- Add Python malformed/stale-contract and negative-drift tests. Extend the dedicated **network-none, tmpfs PostgreSQL** workflow to create the actual Audit V1 migration's table/index, verify both indicators, remove and restore the timestamp default, switch the index to ASC then back to DESC, and confirm that fake history presence **never** produces a release GO.
- Keep the CI test databases isolated and disposable; the operator's real inventory never leaves the approved local environment. No remote services, secrets, production schema edits or Flyway baseline setting changes.

## Initial hosted verification

The initial [Ubuntu GitHub Actions run #40265335775](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40265335775) **succeeded** with 11/11 Python unit tests and the real isolated PostgreSQL 16 container preflight. It verified the actual V1 `DEFAULT CURRENT_TIMESTAMP` and `DESC NULLS FIRST` catalog flags, rejected missing default and same-name ASC-index mutations, restored each defect before the next case, and retained NO_GO/manual review on forged Flyway-history table presence. This is CI evidence for a synthetic test fixture, not a review of operator data.

## Important evidence limits

This is a **bounded V1 metadata predicate**, not a complete Postgres schema-equivalence proof. It does not enumerate all triggers, grants, owners, indexes, database search_path, extensions, Flyway migration rows or checksums. `CURRENT_TIMESTAMP` semantics can be expressed through other SQL spellings and could trigger an intentional manual-review false negative. PostgreSQL generated catalog output must be verified on the actual supported engine. Even a perfectly matching predicate yields only `REVIEW_HISTORY_CONTENT_AND_OWNERSHIP`, not release approval.

**Still requires** actual operator-authorized history and schema inventory, representative historical restore and rollback verification, coordinated Identity/User versioned migrations replacing six Compose `ddl-auto:update` overrides, owner signoff and all independent release gates. [Issue #242](https://github.com/teldigi5-wq/aetheris-platform/issues/242) OPEN; Stage45 NO_GO, all nine certification gates unapproved.

Do **not** merge or deploy based on synthetic tests alone.
