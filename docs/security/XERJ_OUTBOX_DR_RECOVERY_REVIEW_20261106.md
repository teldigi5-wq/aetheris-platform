# Aetheris PostgreSQL User outbox: synthetic backup, restore and guarded downgrade

**Review-only draft child of PR #392** at verified parent `47b0e57e6ea778fc4f13f29bb18577a69a26fc38` (110/110 Java regressions; 90/90 live checks in Actions run #42193286667). This stage is an isolated PostgreSQL disaster-recovery rehearsal. **No production migration, data snapshot, backup deletion or rollback is authorized.**

## Scope, threat and safety guard

Before an outbox rollout, a release owner must know whether historical User and Identity records survive additive migrations, whether complete outbox/approval/audit/privilege state can be restored, and what happens if someone attempts to downgrade after new event data has been committed. The test intentionally demonstrates that deleting outbox tables is **not** a safe rollback once they contain events or recovery evidence.

The runner only executes under `GITHUB_ACTIONS=true`, a hosted ephemeral runner temp directory and an exact `xerj-mtls-ci-RUNID-ATTEMPT` Docker Compose namespace. It connects only to that fresh disposable PostgreSQL 16 container. Its four dedicated database names—`xerj_dr_source`, `xerj_dr_pre`, `xerj_dr_restored` and `xerj_dr_empty`—are distinct from the CI application's own `xerj_ci` database. Neither production hostnames nor external database credentials are accepted.

## Actual required CI checks

1. Seed only synthetic legacy Identity/User rows and create a private PostgreSQL **custom-format snapshot** in the runner temp directory. Apply migration files `20261106_01` through `20261106_04` twice to the isolated source schema. Validate that the four historical records remain with NULL tenant IDs.
2. Seed two synthetic outbox rows (one parked after 12 attempts; one delivered with an explicit UUID tenant ID) plus a consumed approval and a linked recovery audit entry. Take a second private custom-format snapshot. Restore the full migrated schema and data transactionally into an empty independent database using `pg_restore --single-transaction --exit-on-error`. Also restore two pre-migration baseline copies.
3. Verify the restored synthetic event states, recovery generations, approval/audit references, canonical tenant UUID, expected index, SECURITY DEFINER recovery function and revocation of PUBLIC EXECUTE. Compare deterministic SQL digests of the source and restored synthetic rows without printing either row contents or digests.
4. **Negative downgrade:** run the explicit destructive `xerj_outbox_empty_only_rollback.sql` against the **populated restored copy**. It must reject the operation with a nonzero exit status and leave every event, function, approval and audit row identical. The rollback script itself refuses any database name except the two designated disposable DR copies and requires the ephemeral database bootstrap owner.
5. **Empty-only downgrade:** restore the original pre-migration baseline into a separate copy, apply the four migrations, and prove the guarded schema downgrade succeeds only because its outbox/approval/audit tables are completely empty. The original Identity/User fixture records and legacy schema must survive. Verify the original pre-migration snapshot separately.
6. Preserve the previous 110 Java tests, live 90 RSA/mTLS, role-separated RabbitMQ, tenant isolation, DLQ, broker outage/recovery, eight-worker outbox and dual-approval negative checks. The exact-head GitHub Actions run must be COMPLETED SUCCESS before a passing claim. Private dump files are deleted inside a cleanup trap; no raw dump, secret, PII-shaped synthetic rows or app logs are uploaded.

## Not proven / must remain NO_GO

- A pre-migration restore is **point-in-time restoration, not an online non-destructive downgrade**. It can discard writes made after the snapshot. A real production rollback requires approved recovery point/time objectives, read-only/maintenance cutover, reconciliation of in-flight User and Audit events, transactional backup consistency across Identity/User/Audit databases and RabbitMQ, and verified rollback plans for application binaries and API contracts.
- No actual production data layout, size, grants, encryption key, backup destination, retention, backup restore permission, PITR/WAL archive, logical-replication lag or multi-service restore ordering is tested.
- The synthetic runner uses an ephemeral bootstrap superuser; it does not prove production least-privilege restore, tamper-resistant or encrypted/offsite backup, independent human authorizations, complete data privacy deletion, automatic monitoring or disaster-recovery operations.
- A populated outbox may contain PII and cannot be destroyed merely to roll back a migration. Recovery approvals/audit links must not be orphaned. Independent security review, backup owner signoff, validation on anonymized production-like scale and live game-day rehearsal remain mandatory.
- XERJ PR #376 still has historical source and unverified per-file licences/privacy/explicit publication consent. Nothing is merged, deployed, repinned or published.

**Status: Aetheris Stage45 NO_GO · XERJ HOLD.**
