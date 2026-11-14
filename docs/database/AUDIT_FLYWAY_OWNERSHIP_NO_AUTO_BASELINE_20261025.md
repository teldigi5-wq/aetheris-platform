# B4T — Real Audit Flyway history ownership and explicit-baseline refusal

**25 October 2026 — disposable research-only test, stacked after PR #276.**

## Release risk under issue #242

The current **real Audit** application already depends on Spring Boot Flyway and PostgreSQL integration, and ships `audit-service/src/main/resources/db/migration/V1__create_audit_events.sql`. Its runtime configuration, however, contains `spring.flyway.baseline-on-migrate: true`, a separately named `flyway_audit_schema_history` table and `baseline-version: "0"`. On a database with pre-existing User/Identity tables, automatic baseline could conceal migration ownership and bypass explicit database provenance review. Meanwhile, Identity/User have Hibernate `ddl-auto: validate` in application configuration, with production-like Compose overriding `update`. No cross-service migration cutover has been approved.

Older independent Flyway baseline lab #247 and modern synthetic SQL compatibility #276 provide evidence for Identity/User on **synthetic** databases. This B4T slice tests Audit’s **actual on-boot Flyway engine**, its own migration history and a safe refusal to auto-baseline a prepopulated shared schema.

## Exact disposable tests

A new Compose overlay runs real **current Audit Docker image** in three roles on a private no-published-port network with a fresh, tmpfs PostgreSQL container and temporary RabbitMQ. All three force `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false` using CI-only environment configuration. Production Audit `application.yml`, actual V1 SQL, Dockerfile, real application behavior and operational Compose remain unchanged.

1. Create completely **empty** `aetheris_audit_fresh` test database. Boot real Audit image. Flyway must run its **actual V1** and create both `audit_events` and `flyway_audit_schema_history` without a baseline record. Query the actual history to require exactly one successful SQL version `1`; no Identity/User tables in that isolated DB.
2. Boot a **second real Audit container** against the same clean migrated DB with automatic baselining disabled. Actual HTTP requests against both distinct Audit ports/hosts use temporary ADMIN, DEVELOPER, API_CONSUMER, expired, tampered and anonymous tokens from the real Identity `JwtService.issue` implementation. Require **12/12** correct accept/deny outcomes, empty events for ADMIN, and still exactly **one** SQL V1 Flyway history entry without a baseline row.
3. Independently create `aetheris_audit_nonempty` test database with the **exact SHA-pinned PR #247 V1/V2 synthetic Identity/User SQL** but **no Audit history**. Boot the real Audit image with automatic baseline disabled. Startup must fail **for an explicit Flyway nonempty-schema/no-history reason**, not generic Rabbit/Postgres unavailability. After refusal, verify no `flyway_audit_schema_history` and no `audit_events` table appeared; original IdentityAccount table remains.
4. CI always tears down all container and database state. The probe container is unprivileged, read-only, without Docker socket, public internet or external endpoints. No persistent storage, host port bindings, real keys/accounts or production database access.

## Actual initial GitHub Actions evidence

[Implementation run #40187951448](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40187951448) succeeded: CI confirmed the private disposable topology, verified a real **one-row Audit Flyway V1 successful history and zero baselines** on the empty database, demonstrated a second Audit container boot using the same history, and passed **12/12 signed JWT HTTP checks** against two actual application instances. The separate nonempty guarded Identity/User database explicitly **refused silent Flyway baselining**, with no Audit migration history or Audit table left behind. The first run found a disposable PostgreSQL startup race, fixed by waiting for dependency health before creating test databases; a boolean history query was also made unambiguous. **No application Java, shipped Flyway config, migrations or production Compose was changed.** An exact documentation-head rerun is required before final checkpoint.

## What success does NOT authorize

A passing B4T means **the existing Audit Flyway V1 can be tracked and reused when it owns an entirely fresh isolated database with automatic baselining disabled**, and that the same **experimental override** refuses to silently take ownership of a shared, nonempty Identity/User database without history. **It does not mean production Audit baseline-on-migrate is disabled** — it remains `true` in the actual application config and requires a separate approved remediation/migration design before deployment. It also does not validate a real historical audit table, multi-writer concurrency, application upgrades, operator backups/restores, a service-owned schema or cross-schema permissions in production.

The recommended policy is a reviewed, per-service Flyway ownership and explicit baseline process with a strong historical-data preflight and backup/rollback approval. This PR intentionally does **not** edit the production Flyway setting or auto-DDL Compose overrides; all protected branches and real data remain unchanged. Issue #242, Stage45 and the nine release gates remain **NO_GO**. Keep PR stacked, draft, unmerged until reviewed.
