# Flyway opt-in candidate for fresh standalone service databases

**27 October 2026 · REVIEW ONLY · production not approved · NO_GO**

Extends the verified #325 synthetic PostgreSQL schema/restore rehearsal. This PR adds actual Flyway dependencies and SQL migrations to Identity and User, but **Flyway is disabled by default** using `AETHERIS_FLYWAY_MIGRATION_ENABLED:false`. Hibernate remains `validate`. The SQL comes from candidate tables already tested against Hibernate; each service uses its own `V1__fresh_schema.sql` migration path.

### Independent database requirement

Existing Compose can use a shared `aetheris` PostgreSQL database for multiple services, while both fresh Flyway scripts start with version 1. Their migrations must NOT be enabled concurrently on the same nonempty default schema. The integration tests therefore use two **separate** new disposable databases, `flyway_identity` and `flyway_user`. A real release must first approve physical schema separation, roles, privileges, history-table ownership and migration ordering, or explicitly redesign compatible shared-database histories.

### What the test actually does

The CI-only runner requires `AETHERIS_FLYWAY_SYNTHETIC_CI_ONLY=yes`, allocates an ephemeral synthetic password and starts a disposable PostgreSQL 16 Alpine instance bound to host 127.0.0.1:55433. It creates an empty DB per service, explicitly opts into Flyway, applies actual version 1 SQL, then **boots Spring Data JPA with Hibernate schema validation** for each. It confirms genuine `flyway_schema_history` persisted version 1 and that rerunning both tests does not duplicate migration history.

A third synthetic database already contains an incompatible `identity_accounts` table and sentinel row but has no Flyway history. With `baseline-on-migrate=false`, migration should refuse that unknown legacy state **before changing data or creating a history marker**. CI requires the actual Flyway refusal message, verifies sentinel preservation, and checks that no history table was added. Any unexpected start is a failed security test. The disposable instance is destroyed whether CI passes or fails.

### Why production still cannot proceed

This exercise is NOT migration of a **historical existing database**. No existing volume, real customer data, real keys, production runtime, independent DBA-owner-approved baseline, consolidated shared-schema plan or PITR/rollback approval is provided. A separate owner-approved migration strategy must reconcile Identity, User, existing Audit Flyway history, any existing composite DB, and future revocation schema, then test actual representative backups and post-migration multi-service start/order. No baseline-on-migrate shortcut, `update` JPA DDL, `clean`, destructive migration or automatic production activation is permitted. Existing security and tenancy issue #251, runtime provenance/advisory #255/#259, real audited revocation, nine runtime gates, and Stage45 **NO_GO** remain unresolved. Protected main untouched.

This PR is **not approved** for deployment even if CI passes.
