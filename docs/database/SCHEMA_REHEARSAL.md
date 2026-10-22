# Aetheris schema migration — disposable database rehearsal

**Status: EXPERIMENTAL / REVIEW ONLY. Existing-database migration UNRESOLVED.**

This is a non-deployed, synthetic PostgreSQL 16 rehearsal for [security issue
#242](https://github.com/teldigi5-wq/aetheris-platform/issues/242).
The SQL lives under `docs/database/rehearsal/`, **not** in either Spring
service's auto-discovered `src/main/resources/db/migration` directory.
It is deliberately not wired to startup, Flyway, Docker Compose, Helm or any
running system.

## Verified source-level baseline

- `IdentityAccount` maps to `identity_accounts`; `RefreshToken` maps
  to `identity_refresh_tokens` with an FK to accounts and a unique token hash.
- `User` maps to `users`.
- The audit service has a separate active Flyway migration:
  `audit-service/src/main/resources/db/migration/V1__create_audit_events.sql`
  and uses history table `flyway_audit_schema_history`.
- Identity/User production Spring defaults use Hibernate `validate`, but
  all three Compose variants currently set
  `SPRING_JPA_HIBERNATE_DDL_AUTO: update` twice. That startup schema mutation
  risk is **UNRESOLVED**; these rehearsal scripts do not remove it.

## What CI actually verifies

A transient PostgreSQL 16 container starts with **no external network and
no published ports**, using only an ephemeral password. CI creates tables
and validates a foreign key and uniqueness constraint using synthetic test
rows. The container and its anonymous storage are disposed afterward.
No real credentials, user records, database snapshots, production container
or Firebase/Vercel resources are read or modified.

The candidate column types are inferred from the currently checked-in
Hibernate entity annotations and conventions. A matching SQL script is NOT
proof of compatibility with an existing PostgreSQL volume; Hibernate-generated
constraints, identifier sequences, column nullability, Java/JDBC types and
table/index names may differ.

## Required migration acceptance gates, not yet completed

1. Read-only introspection of a **redacted** pre-existing *nonproduction*
   schema and PostgreSQL version. Never paste raw `pg_dump` credentials,
   private rows, password hashes, refresh token hashes or user data into GitHub.
2. Compare existing identifiers, constraints, indexes, sequence ownership,
   Flyway audit history and drift to each candidate DDL statement.
3. Add real Flyway modules and distinct, service-owned history tables only
   after compatibility and migration ordering are tested. Do not run two
   schema owners over the same object or use unapproved `baselineOnMigrate`.
4. Prove a **fresh** environment and a restored synthetic copy of the
   historical schema both start with Hibernate `validate`.
5. Back up and **restore-test** any persistent PostgreSQL installation before
   release. Review transaction and credential rotation ordering, downtime
   and rollback.
6. Only then remove the six Compose `ddl-auto: update` overrides, use
   `validate` and require migration CI and operator approval before merge.

Never run these candidate scripts against an existing volume or use
`docker compose down -v`; it deletes persisted data. This is a proof
of the *proposed schema shape*, not a claim that issue #242 is fixed.
