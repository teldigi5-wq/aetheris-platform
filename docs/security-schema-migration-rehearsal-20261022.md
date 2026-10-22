# Identity and User schema migration candidate — REVIEW ONLY

**Do not deploy, execute against a real database, or merge as an automatic migration.**
This branch introduces additive candidate DDL and a disposable PostgreSQL rehearsal.
It does NOT enable Flyway in Identity/User, does NOT modify Compose DDL settings,
and does NOT modify persistent volumes or production credentials.

## Why

Identity and User presently set Hibernate `ddl-auto: validate` in application YAML,
but the three Compose variants override both services with `SPRING_JPA_HIBERNATE_DDL_AUTO: update`.
The Audit service independently uses Flyway migration
`V1__create_audit_events.sql` and table `flyway_audit_schema_history`.
A new blank DB and an older DB already touched by Hibernate must be handled differently.

## Candidate objects

- Identity: `identity_accounts`, `identity_refresh_tokens`, foreign key,
  email uniqueness, token-hash uniqueness and standard identity columns.
- User: `users` and unique email index/constraint.
- Audit: reuses the existing committed migration in rehearsal; does not change it.

SQL is deliberately not idempotent on already existing tables. This avoids
silently claiming unknown Hibernate-created tables match the candidate schema.
The actual Flyway migration order, migration history table ownership,
legacy baseline version, transactional upgrade semantics, PostgreSQL types,
column constraints and Hibernate `validate` compatibility require a separate,
fully executable review before activation.

## Verification

The PR runs Python contract tests plus Docker PostgreSQL 16 with **synthetic data**
only. It exercises an empty database, uniqueness, foreign-key enforcement,
synthetic record preservation when V1 is rerun, and explicit rejection of an
unknown preexisting table. It does not authenticate a real Aetheris instance
or demonstrate migration from the operator's existing named data volumes.

## Promotion gate

1. Inventory the actual database schema and all active branches/services without
   exposing production data. Export only schema metadata using an authorized
   read-only connection; never copy passwords, tokens or student records.
2. Back up the persistent PostgreSQL volume/database; restore into an isolated
   test environment and record the restore verification result.
3. Compare that isolated copy's schema to fresh Hibernate/entity metadata. Repair
   intentional drift through explicit, reviewed forward migrations.
4. Decide the Flyway history/baseline strategy for Identity and User in the shared
   database without overriding or corrupting Audit's migration history table.
5. Add Spring Boot integration tests against both clean and restored databases
   with `ddl-auto: validate` and Flyway enabled, including failure rollbacks,
   parallel startup and restart idempotency.
6. Only after those tests pass, remove all Compose `ddl-auto: update` overrides
   and enable a controlled migration mechanism with operator signoff.
7. Coordinate with Aetheris release PR #241's DB/RabbitMQ/Redis credential
   rotation, maintenance window and rollback. NEVER use `docker compose down -v`.

Tracked issue: #242. This candidate does not close it.
