# Shared database — Audit Flyway and database-role isolation rehearsal

**27 October 2026 · DRAFT · Stage45 NO_GO · synthetic only · not a production migration**

This follow-up is stacked on PR #327 (Identity/User scoped Flyway on one physical PostgreSQL 16 DB). It does **not** amend existing volumes, migrate real historical data, deploy, merge protected `main`, or rotate credentials.

## Verified code changes (CI evidence required before claiming execution success)

1. Audit's existing application configuration used unconditional Flyway activation, `flyway_audit_schema_history`, and `baseline-on-migrate: true`. This candidate changes the default to `AETHERIS_AUDIT_FLYWAY_MIGRATION_ENABLED:false`, refuses unapproved baselining, disables `clean`, and validates migrations. This may make unmigrated Audit installations fail on first database use; **do not promote before inspecting historical databases**.
2. A new opt-in `@JdbcTest` uses the **existing** Audit V1 migration in `audit_stage`. It overrides production Flyway defaults only inside a disposable test JVM and requires `aetheris.audit.shared.synthetic=true`.
3. `scripts/security/rehearse_shared_db_three_roles.sh` provisions a fresh, ephemeral PostgreSQL 16 container and one database with three distinct schemas. Each migration has a separate login that owns **only its schema**. Three additional app runtime logins receive USAGE on their own schema plus minimal table-level DML, **not their own Flyway history** or CREATE/ownership rights. Passwords are randomly generated for this run, masked in Actions, never stored in Git.
4. Spring/Flyway integration is invoked for Identity and User with actual Hibernate validation and for Audit with a JDBC slice. The script authenticates each login over TCP with its own password, checks required synthetic writes/readback, denies cross-service reads/DDL and migration-history mutation, and denies Audit runtime DELETE of its own append-only event table. It repeats all three startup tests, verifies preserved synthetic rows and one history row per schema, and performs an isolated `pg_dump`/`pg_restore` of the **synthetic** database.
5. Exact-head Ubuntu CI runs source contracts, the genuine ephemeral database rehearsal, full four-module Java tests, and the deliberate Stage45 NO_GO gate. The container is destroyed even on test failure.

## Limits and hard blockers

- This is a **test policy** and a proposed database topology, not production database-role deployment. Compose and Kubernetes secrets/roles remain unconfigured for this model.
- Migration roles own their schemas and need CREATE/DDL for migrations; application runtime roles do not. Real workloads, credentials, backup operators, rotation, revocation, and Audit service connection pooling must still be reconciled.
- Database-level grants do **not** prove application authorization, user-to-object binding, tenant isolation, or prevention of an already-privileged superuser bypass.
- The existing Audit V1 SQL contains `IF NOT EXISTS`; turning off baselining is a refusal policy for unknown nonempty schemas, not a historical compatibility analysis. Check schema fingerprints and Flyway metadata before any actual rollout.
- Shared-database startup concurrency, migration lock/contention, representative historical schema upgrades, full application service boot, production owner permissions, long-term key custody, dedicated audit-writer restrictions, and failover are not certified by this milestone.
- Production requires owner/DBA schema inventory, verified backups/restores, carefully sequenced migration and rollback, least-privilege grants, credentials approval, runtime tests, and nine independent release attestations.
- **Never** use `docker compose down -v`, `flyway clean`, silent automatic baselining, or Hibernate `ddl-auto:update` to force migration success.

Status before exact-head Actions finish: **coded; CI pending; unmerged; NO_GO**.
