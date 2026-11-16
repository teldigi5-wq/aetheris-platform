# S0-E6M — PostgreSQL recovery fencing, privilege custody and migration-gate rehearsal

**9 November 2026 | REVIEW ONLY | Stage 45 NO_GO | Not a production migration or release**

## Exact continuation, no duplicate stages

This is a separate child of **Platform draft [PR #415](https://github.com/teldigi5-wq/aetheris-platform/pull/415)** at immutable `53d55135181ef89d3ff402f46b835d4b3a0f8ac5`. Platform S0-E6J contract [PR #414](https://github.com/teldigi5-wq/aetheris-platform/pull/414) already proved exact-JTI signer/session/epoch PostgreSQL function-only privileges. S0-E6K proved refresh credential rotation and signed session enrollment in one transaction. Runtime S0-E6L [PR #297](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/297) at immutable `816dccdaffb7e5dc1e0a86707dc3829af2ed3b70` already proved **real Platform JWT → actual Runtime direct-loopback HTTP → shared disposable PostgreSQL function-only verifier**. Do not repeat those milestones or merge their draft PR stacks into diverged protected `main`.

The actual Platform Identity service uses Hibernate `ddl-auto: validate`; its `pom.xml` does not install Flyway or Liquibase. No production SQL migrator, versioned on-disk production schema or independent operator signing/enrollment credentials have been approved. **This work creates no automatic `src/main/resources/db/migration` file**, changes no application authentication endpoint and never connects to production.

## What S0-E6M actually adds

- [Machine-readable review manifest](migrations/s0-e6m/review_manifest.json) pins the exact parent commit, Runtime contract and [original review SQL](../../identity-service/src/test/resources/security/e6j_runtime_session_ledger_pg16.sql) Git blob `fcf0e61c24ab89b61f10b6d2e454742a4b0420b0`. The review manifest explicitly records `production_migration_installed: false` and release `STAGE45_NO_GO`.
- [Executable PostgreSQL16 rehearsal](../../tools/security/s0_e6m_disposable_pg16_rehearsal.sh): fails closed unless executed in an isolated GitHub Actions container using exact localhost, disposable `aetheris_e6m_disposable` database and CI-only marker. It first injects a deliberate transactional DDL error and confirms that the partial schema rolled back while a completely unrelated pre-existing row remained intact.
- Runs the **unchanged** S0-E6J review contract SQL. Confirms non-superuser `e6j_exec_owner` actually owns the signed-session-check SECURITY DEFINER function, and verifies a real independent network-authenticated reader's access to only that function (not session table SELECT/UPDATE, not enroll or revoke). Distinct, automatically generated short-lived PostgreSQL LOGIN passwords for issuer, reader, revoker exist only inside runner memory and are not committed or displayed.
- Creates one synthetic signer, epoch, enrolled JTI and audit event. A genuine independent issuer LOGIN is able to enroll, a reader LOGIN sees active status, and a separate revoker LOGIN revokes that session; the original DB's reader then denies it.
- Creates a real `pg_dump -Fc` snapshot **before** revocation. Restores that snapshot via `pg_restore --single-transaction --exit-on-error` into another disposable PostgreSQL database. Tests restored unrelated data, original function owner, reader-only grants, and demonstrates that **an unfenced restore reauthorizes an already revoked session**. This is a vulnerability reproduction, NOT a production authorization decision.
- Applies the [restore deny fence](migrations/s0-e6m/restore_fence.sql) with all traffic already disabled by the isolated test. Under one short exclusive table lock it writes a DB-local audit entry for each currently ACTIVE signer and session, marks all restored signers and sessions REVOKED and sets `revoked_at`. The restored reader now **denies** the old session. A second invocation must produce no new audit row or newly permitted session. The unrelated backup baseline remains unchanged.
- Deletes ephemeral snapshot files and the isolated restored database, schema and temporary roles. No customer data, permanent migration, production credentials, app route, deployment or protected branch are changed.

## Release safety interpretation

**A database restore can undo a logically irreversible revocation.** A previously signed, not-yet-expired access token that was revoked *after* the backup could become authorized again after restoring stale active registry rows. Replaying only a SQL backup is insufficient to preserve revocation truth.

The safe restore ordering for a *future, independently reviewed production design* must be:

1. Have written owner/operator approval for change window, backup policy, incident recording, DNS/ingress controls, expected fail-closed duration and the exact signed migration artifact. Gather external independent logs or a high-watermark for all post-backup revoked sessions, epochs and signer keys.
2. **Stop all Runtime, Gateway, Identity, worker, automation and standby connection paths** to every candidate restored primary. Prove no public backend direct-port bypass, no read replica serving stale authority and no alternate PostgreSQL primary. Simply setting a Boolean or CI environment variable is **not operational proof** that real traffic is stopped.
3. Verify backup provenance, encryption, transfer permissions, checksum, tool versions, consistency, WAL restore point and all materialized identity/tenant/principal records. Restore into isolated shadow storage first; verify owners, schema, grants, table constraints, functions, default privileges and security definer `search_path`.
4. **Before any traffic can reach restored data**, atomically invalidate ALL restored signing keys and sessions (or use an independently maintained, externally monotonic recovery epoch that survives restores). Preserve audit with correlated incident/reference identifiers. Test a previously revoked JWT fails the independent Runtime preflight, and test both issuer and revoker roles cannot escalate.
5. Rotate signing keys and workload credentials; reconcile legitimate owner/tenant object memberships, restart controlled trusted Identity issuer first and re-enroll new sessions only after current data authority is independently verified. Reject all old JTI values. Canary only against an authorized staging identity and explicitly fail closed on DB read-only/connection fault.
6. Promote only after reviewed recovery and rollback proof. **Do not** treat rolling back only application binaries as reversing a security migration: database versions, grants, backups, renewed key custody and revoked session state need their own compatible recovery path. Dropping the schema is **not** a safe production rollback once any valid sessions or audit records exist.

**The included fence is NOT an authorized production operating procedure.** It has DB-local audit only; no separately immutable external log, no human/second-person approval, no production-ingress shutdown proof, no actual restore PITR/WAL replay, no TLS verification, and no reviewed role delegation with least-privilege secret handling. It must not be copied into a live database or launched by app startup. The snapshot and synthetic-role setup are CI-only.

## Verification and blind spots

The new [S0-E6M pinned CI workflow](../../.github/workflows/s0-e6m-disposable-postgres-restore-fence.yml) executes separate jobs: actual ephemeral PostgreSQL16 `pg_dump`/restore/roles and prior 32 focused Identity JWT/refresh/workload-policy regressions with no skips. The first review-code checkpoint [Actions #42592394069](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42592394069) **COMPLETED SUCCESS 2/2 jobs** and showed:

- Deliberate failed DDL left no partial schema and preserved unrelated records.
- Independently logged-in reader lacked direct table SELECT/UPDATE and issuer/revoker functions, yet could check registered JTI.
- A restored copy of a previously active backup DID resurrect a session revoked after snapshot.
- Mandatory session+signer restore deny fence made the same verifier return false. Replaying the fence did not widen permissions or change unrelated records.
- All 32 existing focused Identity tests passed, zero errors/failures/skips.

That exact run preceded the later enhancement to also audit **each** invalidated restored session, in addition to each signer, and to require preserved post-restore reader ACLs. Final-source-head proof must be checked separately before completion claims.

## What remains unimplemented and why it matters

- A real production-quality versioned migration driver (Flyway/Liquibase/etc.), initially run in isolated staging with immutable schema history, compatible read/write mode, reviewed `CREATE ROLE` grants and `SECURITY DEFINER` owner custody. No auto-migration side effects on login or Runtime startup.
- Independent network client credentials bound to Identity / Runtime workloads, managed rotations and backend TLS/mTLS certificate verification. The CI LOGINs prove privilege isolation **without proving encrypted transport**; the container defaults must not be mistaken for an approved TLS trust chain.
- Permanent transactionally atomized owner/tenant/project/task resource SQL and ownerless-record migration policy. A passing JWT or session preflight is NEVER resource authorization. Runtime tasks/missions stay unconditional 404; global SSE remains disabled.
- Physically verified owner workstation, staging quorum/replica consistency, anti-rollback of signing epoch state, external immutable incident audit, authenticated Gateway-to-Identity token-exchange caller, and approval/rollback drills. Review-only synthetic backup is not a production recovery attestation.

**Next distinct milestone S0-E6N:** choose/validate one deployment migration tool and secure service credential/TLS topology, then build a separate staging-only, approval-gated migration and independent runtime role/transport acceptance with rollback plan. Do not repeat S0-E6J/K/L or reopen finished 34/34 Platform blueprint stages. Keep Platform Security #251 and Runtime #23 OPEN; Stage 45 NO_GO; XERJ HOLD; physical PC `BLOCKED_PENDING_HARDWARE`.
