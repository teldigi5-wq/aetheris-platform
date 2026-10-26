# B4X — Audit Flyway history-content preflight and independent checksum denial

**26 October 2026 — review-only stacked draft after PR #280. Not deployment approval.**

## Scope and reason

Prior Audit schema inventories checked table shape and the presence of the Flyway history table. They did not establish that the table contained exactly the expected successful V1 record, nor prove that a recorded checksum matched the current migration SQL.

The paired read-only SQL and Python classifier inspect only bounded **Flyway history row metadata**, without exposing record values, actual checksums, installer names, timestamps, user data or credentials. The SQL runs in a PostgreSQL READ ONLY transaction and requires an existing history table. It emits six booleans and a contract version, verifying single-row history, expected V1 version/script/type/description/success metadata, absence of BASELINE or failed/unexpected entries and presence of a non-NULL checksum. The classifier only produces NO_GO or REVIEW; it **never authorizes release and never exits zero**.

The history-record labels must match the exact shipped V1 script: V1__create_audit_events.sql, version 1, description 'create audit events', SQL type, installed rank 1. **Matching labels are not proof the migration executed.** A non-NULL checksum is not proof of an authentic checksum: this requires running the actual Flyway validator against the approved source migration.

## Operator-only private usage

After B4W catalog review, run this query **only on a DBA-approved private restored snapshot/read replica** using a least-privileged account. Do not run through CI against production or upload inventory material.

1. Execute: psql -X -qAt -v ON_ERROR_STOP=1 -f docs/database/rehearsal/audit_flyway_history_readonly.sql > PRIVATE_AUDIT_HISTORY_FLAGS_JSON
2. Execute: python3 scripts/audit_flyway_history_preflight.py PRIVATE_AUDIT_HISTORY_FLAGS_JSON
3. Treat every NO_GO or REVIEW result as **not approved**. The tool exits 1 for these states, 2 for invalid input. Keep the private JSON offline.
4. Separately review actual migration rows/checksums and run real Flyway validation against a representative historical restore after operator authorization.

## CI experiment and evidence limits

The dedicated B4X GitHub Actions workflow is designed to build the **real Audit Docker image**, apply its actual Flyway V1 to a fresh disposable PostgreSQL instance, check the genuine Flyway history using read-only booleans, reject synthetic missing checksum/failed row/wrong script cases, and then deliberately alter a non-NULL checksum. The offline metadata checker will still require review; an **independent real Audit startup must refuse the corrupted checksum**. The CI setup is internal-network-only, no exposed ports, PostgreSQL on tmpfs, synthetic secrets, unconditional teardown.

A passing run proves only these isolated assertions. It does not verify real operator databases, legacy schemas, restore integrity, cross-service migrations, rollback or production availability.

## Initial hosted evidence

[Actions run #40266060328](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40266060328) completed **successfully** on the initial B4X implementation commit. All **10 Python tests** passed. The real Audit Docker build, actual V1 history creation, fixed-boolean SQL inventory, missing-checksum/failed-row/wrong-script negative controls, and **real second Audit startup refusal on an altered non-NULL checksum** passed. Workflow cleanup ran successfully. This is only evidence from CI-owned synthetic infrastructure. A final CI rerun is required after documentation updates.

## Release gates unchanged

Issue #242 remains OPEN. Identity/User Compose overrides using ddl-auto:update (six in three Compose files), real operator schema/history inventory, backup/restore rehearsal, migration ownership/rollback, issues #251/#255/#259, Stage45 NO_GO and nine unapproved certification gates remain unresolved. **Do not merge, baseline, deploy or alter persistent databases.**
