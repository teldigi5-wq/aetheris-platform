# Security closure: synthetic fresh/legacy PostgreSQL restore + actual Hibernate validation

**27 October 2026 — DRAFT, isolated test-only, NO_GO.**

This candidate is stacked on the main-compatible hardened release chain: PR #315 (secrets/network/certificates), #322 (hard NO_GO), #323 (direct backend JWT), and #324 (all six Compose Identity/User `ddl-auto` settings set to `validate`). It adapts the previously green PR #243 (verified GitHub Actions #39799544311), while preserving its SQL as **candidate-only files outside runtime Flyway discovery**.

### Executable proof

- In a freshly created disposable PostgreSQL 16 container bound to runner **127.0.0.1 only**, run candidate additive Identity/User table creation plus the existing Audit fixture. No real service database, customer data, existing volume, or production Flyway history is touched.
- Insert synthetic Identity and User rows, reject duplicate email/orphan refresh tokens. Assert unknown legacy tables refuse candidate SQL rather than silently baseline or overwrite.
- Take **real `pg_dump -Fc` backups of both a fresh seeded database and a deliberately incompatible legacy sentinel database**. Restore each to an additional uniquely named synthetic database using `pg_restore --exit-on-error`. Assert known synthetic Identity/User rows and FK are preserved on the fresh restore, and legacy sentinel content persists while still refusing candidate SQL.
- With that schema in place, independently start the **actual Spring Data JPA/Hibernate `ddl-auto=validate`** test slice for Identity and User against PostgreSQL. These existing source tests are enabled only with `-Daetheris.schema.rehearsal=true`, never ordinary production startup. Isolated CI owns the test-only PostgreSQL container and tears it down in a shell trap.
- Exact-head CI also verifies that none of the three reviewed Compose variants re-enables Hibernate auto-mutation, and the release remains a machine-readable NO_GO.

### Why this is not a production migration

These two candidate SQL scripts are **NOT installed as Flyway migrations** and no existing database is baselined or upgraded. A real historical database may have unknown tables, sequences, naming, schema owners, permissions, constraints, data and Flyway checksums. The synthetic legacy restore demonstrates **preservation and refusal**, not a supported upgrade. The production operator must still inventory live historical schemas, recover/test representative backup copies, review and approve Flyway baseline/versioned migrations, test concurrent starts and rollback, sign off secret rotation and enforce tenant identity. Prior to those approvals, changing the Compose `validate` default may prevent normal startup on any unmigrated database.

**No protected-main merge, live secret rotation, existing-volume modification or production release.** Security #242 historical migration, #251 tenant auth, #255/#259 supply chain, real emergency revocation, Stage45 13 safety blockers and nine independent runtime gates remain open. E3.9/E4–E9 require separate certification.
