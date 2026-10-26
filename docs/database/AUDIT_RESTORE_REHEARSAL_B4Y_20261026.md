# B4Y — disposable Audit PostgreSQL backup/restore and Flyway restart recovery

**26 October 2026 — stacked DRAFT on [PR #281](https://github.com/teldigi5-wq/aetheris-platform/pull/281). NO_GO for production.**

## Scope: reusable test, not historical operator evidence

B4X already proves real Audit Flyway V1 history generation and checksum-mismatch refusal on ephemeral PostgreSQL. Earlier [PR #275](https://github.com/teldigi5-wq/aetheris-platform/pull/275) tested a synthetic User schema backup and Hibernate validation. B4Y adds the **Audit-specific restored-data and Flyway-history continuity experiment** without changing service Java, Audit V1 SQL, shipped Flyway settings, production Compose or persistent databases.

There is **no operator database or real historic backup** available to this draft. CI creates a synthetic Audit database from the actual shipped Flyway V1 SQL, inserts one invented audit event with no name/email, then tests archive integrity and recovery in two completely new disposable PostgreSQL databases. Do not call this a production backup exercise.

## B4Y measured acceptance contract

1. Preflight the merged test-only Compose configuration: one internal Docker bridge, no published host ports, no named DB volumes, PostgreSQL tmpfs, synthetic credentials, no privileged containers or Docker socket, no Flyway auto-baseline injection. Confirm exact source V1 blob.
2. Build the **actual** Audit Docker image. Flyway must create one successful V1 history row on a new temporary PostgreSQL database.
3. Seed exactly one invented Audit event through SQL solely in the CI-owned database; never create a customer record, use production credentials or connect to external services.
4. Run **real** PostgreSQL 16 `pg_dump -Fc` to an archive held inside the disposable PostgreSQL container. Record and verify SHA-256 of that archive; ensure it contains the Audit table and Flyway history.
5. Restore the same archive with `pg_restore --exit-on-error` into **two other new database names**. Compare complete normalized schema and data SQL dumps of the original and good restored database. Ignore only PostgreSQL's randomized `\\restrict` and `\\unrestrict` *psql client markers*; never ignore DDL, schema constraints, SQL data, migration metadata or application rows.
6. Run the existing B4W schema and B4X Flyway-history preflights on the good restore. Both must remain explicit **REVIEW**, not a GO or success-zero exit.
7. Start an independent real Audit service on the restored database using **the shipped Flyway configuration**; probe real HTTP health and denial of unauthenticated `/api/events` (401). Check the same single V1 entry and synthetic event still exist. Compare canonical schema/data before/after the real restart; verify the untouched archive hash.
8. On the **separate** restored clone only, corrupt Flyway's stored V1 checksum and require a second real Audit process to exit nonzero for checksum/validation mismatch. The good restored database must remain intact. All containers, temporary archives and network must be removed unconditionally.

## Initial exact implementation evidence

[Hosted Actions #40276232332](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40276232332) **completed successfully** after a test-only import error in the first attempt was corrected without relaxing any security assertions. **7/7 Python unit tests** passed. The real Audit image and bundled V1 migration booted on a fresh synthetic PostgreSQL database; a custom-format backup archive passed SHA-256 integrity verification and restored into **two new disposable PostgreSQL databases**. Canonical schema/data from the clean restore matched the source, B4W/B4X preflights required review rather than release GO, the genuine restored Audit service returned healthy HTTP and denied anonymous events with 401, and its restart left schema/data and the backup digest unchanged. A separate corrupted-history restore refused startup with a Flyway checksum/validation error and left the good restored database intact. All resources were cleaned up by the workflow.

The initial version of this PR first failed **before any backup operation** because a new Python test imported `sys` without declaring it. The exact failure was repaired by adding that missing import, and the subsequently passing CI run is the valid implementation evidence. A new final-head run is required if docs or CI change after this run.

## What a PASS means — and does not mean

A successful CI run is scoped evidence that **a fresh synthetic Audit V1 database** can be backed up, restored into an isolated clone and started without altering canonical schema/data. It also proves that deliberately mismatched Flyway history in another clone prevents startup. It cannot prove the state or recoverability of historical operator data, restore time objectives, actual backup encryption, secret rotation, data corruption absence, multi-service ordering, workload replay, performance, active operational data ownership or rollback of a production migration.

Operational promotion requires a human/DBA-approved **representative historic database clone**, private schema and Flyway history inventory, actual backed-up data integrity and restore results, explicit ownership/baseline/forward-only migration design for Identity/User/Audit, TLS/network/secret validation, operator restart/rollback window and owner approval. Never run this CI workflow or its cleanup command on retained databases.

## Release status

Security issue [#242](https://github.com/teldigi5-wq/aetheris-platform/issues/242) remains OPEN. Six operational Compose Hibernate `ddl-auto:update` overrides remain untouched; #251, #255, #259 and remaining operational release gates also remain unresolved. Stage 45 NO_GO, all nine certification gates unapproved, physical-PC tests pending. **Keep this PR draft and unmerged.**
