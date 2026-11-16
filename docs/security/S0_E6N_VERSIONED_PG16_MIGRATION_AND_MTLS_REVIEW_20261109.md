# S0-E6N — versioned PostgreSQL16 session-contract migration and client TLS acceptance

**9 November 2026 — DISPOSABLE CI STAGING REHEARSAL ONLY — not installed in staging or production — Stage 45 NO_GO.**

## Verified continuation and boundaries

This review branch follows Platform [DRAFT PR #416](https://github.com/teldigi5-wq/aetheris-platform/pull/416) at exact `625a6eea416605c33e9faf9ae02e525af19f1f08`. The parent S0-E6M run had already proved PostgreSQL backup restore can resurrect previously revoked JTI sessions and that an all-session/all-signer deny fence blocks those restored grants. Platform DRAFT #415 already implements isolated RS256 session enrollment; Runtime [DRAFT #297](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/297) at `816dccdaffb7e5dc1e0a86707dc3829af2ed3b70` already verifies real Platform-issued JWTs through actual Runtime loopback HTTP and a shared disposable PostgreSQL registry. **Do not repeat E6J–E6M.**

The current Platform Identity `pom.xml` does NOT include Flyway or Liquibase and `application.yml` sets Hibernate `ddl-auto: validate`. `docker-compose.core.yml` still has local development PostgreSQL credentials, published port 5432, and a separate `SPRING_JPA_HIBERNATE_DDL_AUTO: update` setting. These are **not approved production database migration, secret handling or TLS transport arrangements**. This branch deliberately does not change or activate the existing compose/deployment topology, application startup, runtime routes, real secrets, or the protected branches.

## New review-only executable functionality

### 1. Transactional, pinned review migration

`tools/security/s0_e6n_versioned_review_migration.sh` is a CI-only migration prototype, not Flyway or an approved production installer. Before doing anything it requires exact GitHub Actions marker, CI scope, loopback PostgreSQL host/port, explicitly named disposable database and user, `PGSSLMODE=verify-full`, and trusted CA file; it verifies the **immutable E6J source Git blob** `fcf0e61c24ab89b61f10b6d2e454742a4b0420b0`.

It records migration version `S0_E6N_001`, the source Git blob SHA and current SHA-256 digest in isolated `e6n_review_meta.applied_migrations`. It takes a transaction-scoped advisory lock, refuses an already applied version, and uses `psql --single-transaction` for the checked exact E6J source contract plus migration journal insertion **in one transaction**. This preserves the original versioned SQL without copying an unreviewed, divergent migration body into application resources. `--verify` checks journal integrity, actual schema, non-superuser function owner and encrypted database connection. Repeated `--apply` is denied; it is **not silently skipped or re-run**. No table or function is ever applied to a customer database.

A production replacement would require signed migration artifacts, operator-owned deployment history, cross-deploy upgrade/rollback compatibility, controlled resource backups and versioned migrations on isolated staging followed by independently approved production promotion. Do **not** point this CI script at staging or production.

### 2. Real PostgreSQL 16 verified TLS and independent client certificate identity

`tools/security/s0_e6n_disposable_pg16_mtls_rehearsal.sh` configures TLS **only inside the exact disposable postgres:16 Actions service container**. It creates ephemeral root CA and server credentials with SANs `localhost` and `127.0.0.1`, and three distinct client certificates with Common Names matched to the reader, issuer and revoker PostgreSQL LOGIN roles.

- libpq `sslmode=verify-full` checks server CA and hostname. Plaintext network attempts and a wrong server hostname are **actively rejected**.
- The PostgreSQL `hostssl` certificate-auth records require a trusted client certificate with CN matching the requested login; wrong or absent client certificates and unencrypted connections are **rejected**.
- The separate real client certificate logins inherit only `e6j_reader`, `e6j_issuer` or `e6j_revoker` function privileges; no table SELECT/INSERT/UPDATE rights are granted to them.
- The issuer actually enrolls a synthetic JTI through TLS. The reader verifies the exact signed-session tuple; the revoker immediately denies it. The reader cannot invoke the issuer function or directly read session tables.
- The review-specific migration journal/schema and synthetic login roles are explicitly removed. Generated certificate private keys are in a temporary owner-only runner directory and removed on job exit. They are **not** committed, uploaded as CI artifacts, installed on any real application or retained as long-lived credentials.

The **CI admin role uses password authentication over server-verified TLS**, while scoped read/issue/revoke roles use real PostgreSQL mutual certificate authentication. This does not prove an external identity platform, certificate rotation, CRL/OCSP enforcement, workload SPIFFE identity, HSM-backed issuer key custody, managed cloud network topology or TLS for actual Java application connections.

PostgreSQL's documented `hostssl`, `clientcert=verify-full` and libpq `sslmode=verify-full` semantics underpin the gate:
- https://www.postgresql.org/docs/16/auth-pg-hba-conf.html
- https://www.postgresql.org/docs/16/libpq-ssl.html

## Actual test evidence

At initial code/manifest checkpoint `3f2e5cd8f04fd678077e52d972567ac9817e9e3e`, [Actions #42595268129](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42595268129) completed **SUCCESS, 2/2 jobs**:

- Real PostgreSQL 16 certificates, hostname verification, plaintext/certificate mismatch denials, unique client-CN logins, function-only grants, session enrollment/read/revocation, versioned schema and checksum journal, duplicate migration refusal, and zero-state cleanup **all passed** as mandatory shell failure gates.
- Existing focused Identity, browser refresh, Runtime RS256 audience-token and workload policy regressions **32/32**, zero failures/errors/skips.
- Source pin and machine-readable `NO_GO` manifest checks passed; no Identity application migration directory or Runtime HTTP activation was introduced.

Subsequent documentation/roadmap-only commits require a new exact-head CI check before their checksum is described as verified. Tests are limited to disposable GitHub Actions runners; they do **not** provide production deployment, owner-PC or staging PKI certification.

## Non-negotiable production promotion gates — not yet satisfied

1. Select and separately review a real versioned production migration tool and frozen artifact publication process; establish full backup, restore fence, immutable external revocation audit, anti-rollback epoch high-watermark and reviewer/operator authorization. Preserve data, do not drop schemas as rollback.
2. Replace local development database-password defaults, Docker port exposure and `ddl-auto=update` settings for the actual production deployment through separate security-reviewed configuration. Establish network segmentation and least-privilege service identities before migration. Never silently change the current compose to claim this proof.
3. Provision real PKI and secret custody, distribution, short-lived credentials, server-trust validation and rotation/revocation for Gateway→Identity, Gateway→Runtime, Identity→PostgreSQL and Runtime→PostgreSQL. The CI certs are not production artifacts.
4. Execute independent two-user **resource-level** tenant/project/task/mission ownership authorization with signed JTI, session authority, workload fence and object checks in the same SQL operation. A successful HTTP preflight releases its row locks before the handler and is not sufficient for actual object permission.
5. Certify rollback and fail-closed behavior on actual primary/replica topology, outages, restored old tokens, TLS handshake errors and cross-node revocation. Obtain owner-PC acceptance and independent attestations before any promotion or externally reachable API.

**Next distinct milestone S0-E6O:** prepare actual staging promotion architecture and resource-level owner authorization contracts, with operator approval gates and independent transport/rollback verifiers. Keep Runtime #23 and Platform #251 OPEN; Stage45 NO_GO, XERJ HOLD, owner-PC `BLOCKED_PENDING_HARDWARE`; historical Platform 34/34 remains complete. This review does not create Stage35.
