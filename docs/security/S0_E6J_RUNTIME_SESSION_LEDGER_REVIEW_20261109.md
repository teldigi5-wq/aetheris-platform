# S0-E6J — Runtime session ledger and PostgreSQL authority-role contract

**9 November 2026 | Review only | Do not migrate or activate | Stage 45 NO_GO**

## Exact historical dependency

This is a narrow child of the existing unmerged [S0-E6I Platform PR #352](https://github.com/teldigi5-wq/aetheris-platform/pull/352) at `0e67b54c11f55944653661e15fcd7963f7634ff0`. The earlier E6D–E6I work already includes a dormant RS256 Runtime-audience issuer, actual source-pinned Platform→Runtime token-verifier interoperability, an internally transactional refresh-token exchange, real PostgreSQL refresh rotation/rollback, an opt-in Gateway SPIFFE identity policy and an isolated real Identity HTTPS/Tomcat mutual-TLS probe. **Do not recreate or merge those draft PRs blindly.** Protected Platform `main` and Runtime `main` have independent, newer changes; this branch does not rebase or merge either.

Runtime's [draft PR #296](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/296) adds independent PG16-backed HTTP session preflight but still points at `synthetic_session_registration` / other disposable fixtures. **No shared deployed registry exists.** The current Platform production `main` still issues legacy HMAC access tokens; the E6D–E6I RS256 capabilities are separate draft-only work. None of these previews is an approved deployable integration.

## New, executable S0-E6J security contract

The SQL is in `identity-service/src/test/resources/security/e6j_runtime_session_ledger_pg16.sql`; it is **deliberately a test resource**, NOT a production Flyway/Liquibase migration or a production runtime schema initializer. CI executes it using native PostgreSQL `psql -X -v ON_ERROR_STOP=1` only in a disposable database, then tears it down.

In a dedicated `e6j_runtime_auth` schema, it creates:

- `signer_key_states`, `principal_epochs`, `session_registrations` (exact issuer, key ID, JTI, principal UID, issued-at and expiry; active/revoked state), and `authority_audit` (unique per-command UUID, action and actor class).
- Separate NOLOGIN database roles: `e6j_exec_owner` (non-superuser, owns the authority tables and `SECURITY DEFINER` functions), `e6j_reader`, `e6j_issuer`, `e6j_revoker`, and an unprivileged `e6j_outsider`. The database owner in the *disposable test* is privileged for setup and cleanup; these roles are not installed anywhere else.
- `enroll_signed_session`: issuer-only function, exact-session/epoch/signer checks, maximum 300-second synthetic token lifetime, unique registration and audit event in the same database transaction.
- `revoke_signed_session`, `block_signing_key`, `advance_principal_epoch`: revoker-only functions, one-way active→revoked and monotonic principal epoch; every successful security mutation writes an audit event atomically. Duplicate audit request IDs roll back the attempted security mutation.
- `check_signed_session`: reader-only boolean function, returning no token, user profile, raw session row or tenant object. Inside a short writable READ COMMITTED transaction it performs the exact registered-session/signer/epoch/expiry/primary check with `FOR SHARE` locks. **Runtime reader receives no direct table SELECT or DML privileges.** This fixes a real PostgreSQL privilege finding: direct `SELECT ... FOR SHARE` requires row-lock privileges incompatible with a minimal table-reader role. The function uses schema-qualified SQL and `SET search_path = pg_catalog, pg_temp` to reduce schema-hijack risks.

**Safety:** No SQL function verifies an RSA signature itself. Session enrollment is trustworthy **only if a separately authenticated, constrained Identity issuer** supplies facts from its genuinely signed token within the correct transaction. A caller with issuer-function access can otherwise enroll invented claims. CI uses synthetic privilege switching; it does **not** prove independent network-authenticated database credentials, production key custody, human emergency approval, external immutable audit or multi-primary safety. A PostgreSQL superuser/owner and a restored database can still override this audit.

## Executable, exact-code evidence

[GitHub Actions #42574878212](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42574878212), source `5a3db269d5f636b84169225e0c96b5beaef5c0c1` — **COMPLETED SUCCESS, 2/2 jobs** on 9 November 2026:

- **6/6 actual PostgreSQL16 tests** in `RuntimeSessionLedgerPg16IT`: independent verifier privileges, issuer-only registration, revoker-only and atomic session audit, strict negative input, signer/epoch monotonicity, duplicate audit request rollback, and a real concurrent session FOR SHARE lock that blocks a revocation update until bounded statement timeout. Zero failures/errors/skips. SQL deployed only to `aetheris_e6j_disposable`, tested via a dedicated CI-only marker, with teardown assertion.
- **32/32 focused existing Identity/refresh/JWT-issuer/workload-policy regressions** across six test classes, zero failures/errors/skips. These do not imply the full Platform reactor, E6I real HTTPS fixture or external Runtime binary were rerun in this S0-E6J workflow.
- The initial CI runs exposed JDBC multi-statement/PLpgSQL parsing, a dollar-quote transcription error and a PostgreSQL privilege issue; changes preserved strict test gates and resolved these rather than granting the reader UPDATE privilege, downgrading locks or ignoring failures.

This is a **schema/role contract test**, not live authorization or a production migration. No application routes, tokens, credentials, RabbitMQ, Dashboard, Kubernetes configuration or the certified Runtime SHA were modified.

## Required next milestone — S0-E6K (not yet implemented)

1. Operator-review a **versioned production migration and backup/rollback** plan, DB namespace ownership, distinct real network client credentials, least-privilege function owner, credential and audit custody, and behavior during restore/epoch rollback.
2. Integrate existing signed `RuntimeAudienceTokenIssuer` / transactional refresh exchange with **atomic, authenticated enrollment** before returning any token. Verify enrollment failure rolls back both new Runtime session and consumed refresh credential; a bare signed JWT without enrollment must remain unusable. Prove role downgrade/revocation and replay against actual independent PostgreSQL clients.
3. Update the Runtime review authority SQL to use the **same approved namespace/function contract**, not synthetic old fixtures, and run two independently signed user identities across Identity and Runtime against real PostgreSQL with direct backend access and exact lock+expiry semantics.
4. Separately verify production-grade Gateway→Identity and Gateway→Runtime mTLS/PKI rotation, tenant membership, task/mission ownership, old ownerless task migration, and safe HTTP/SSE activation. Do not infer readiness from a passing review-only lab.

**Runtime Security #23 OPEN; Platform Security #251 OPEN; Stage 45 NO_GO; XERJ HOLD; owner-PC BLOCKED_PENDING_HARDWARE.** This does **not** create a Stage 35. The historical Platform master roadmap stays 34/34 complete.
