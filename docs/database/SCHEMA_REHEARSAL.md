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

## Second stage: synthetic existing-database upgrade and restore

A new, **test-only** CI workflow now seeds three disposable databases:
a compatible *invented* Hibernate-like baseline with identity/user records,
a baseline with a deliberately altered User email-column type, and tables
without an experiment authorization marker. The migration-like delta merely
adds the extra unique refresh-token index. It is **not a production Flyway
migration** and is not enabled for any running application.

The candidate SQL requires the `CI_ONLY_SYNTHETIC` marker and checks the
expected table names, column widths and refresh-token foreign key before
indexing. It fails closed on a drifted schema or absent marker. Hosted CI
rehearses repeat execution, compares data fingerprints before/after,
creates and restores a PostgreSQL archive and checks the synthetic records
and index survive recovery. The test uses an ephemeral container without a
published port or external network. No real user data or existing backups
are accessed.

**Remaining gaps:** an invented baseline is not the owner's historical
Hibernate-generated database. A passing experiment cannot establish a
correct Flyway baseline, DDL concurrency, real rollout performance, actual
credential rotation, or production recovery. Existing database migration
remains **UNRESOLVED** until an operator-approved, redacted schema inspection
and rehearsed restore of representative existing data is performed.

## Third stage: private, read-only schema catalog inventory

The standalone `docs/database/rehearsal/catalog_inventory_readonly.sql`
query reads **PostgreSQL catalog metadata only**, restricted to the expected
Identity/User table names, known column names and constraint checks. It
never selects account rows, passwords, password hashes, refresh tokens or
user emails, and runs in a read-only transaction.

`scripts/schema_inventory.py` compares the resulting small JSON document
to the known entity schema and fails closed on missing tables, column width/
nullability drift, missing primary/unique/foreign-key constraints or the
expected refresh-token index. The checker prints only fixed diagnostic codes
and never raw input. It does **not** connect to any database itself.

For an operator-approved nonproduction schema review, run the SQL using an
**independent, least-privilege read-only PostgreSQL account** on the selected
private system, save output into a local private folder, then run the
checker against that JSON file. For example (do not paste real connection
strings, environment values or metadata into GitHub):

```bash
# On an approved host, with private PGHOST/PGPORT/PGDATABASE/PGUSER and
# local protected credential handling configured outside the repository:
umask 077
psql -X -qAt -v ON_ERROR_STOP=1 \
  -f docs/database/rehearsal/catalog_inventory_readonly.sql \
  > /private/operator-folder/schema-inventory.json
python3 scripts/schema_inventory.py /private/operator-folder/schema-inventory.json
```

Review the JSON privately and delete it in line with local data-handling
policy. Do not upload this output as a CI artifact or commit it into Git;
database structures can themselves be sensitive. No actual operator system
has been connected to, inventoried or approved by this CI stage.

Hosted tests use a disposable PostgreSQL 16 instance with synthetic rows
and **no external network connection**, proving expected metadata approval,
a missing-index rejection and column-width drift refusal. Real schema drift
may be different, and this stage still does not authorize actual Flyway
baselining or `ddl-auto: update` removal. Migration remains **UNRESOLVED**.
