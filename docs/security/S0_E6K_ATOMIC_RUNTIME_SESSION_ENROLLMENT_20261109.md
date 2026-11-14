# S0-E6K — atomic signed Runtime session enrollment and Identity refresh rotation

**9 November 2026 | DRAFT, review-only | Stage 45 NO_GO | Never deploy as-is**

## Verified lineage — no repeated milestones

Parent: Platform [draft PR #414](https://github.com/teldigi5-wq/aetheris-platform/pull/414) at `63a084f22a2ba7b1c5f03b86e2874cdc16e42cad`. Parent adds the real disposable PostgreSQL16 function-only session ledger and roles. Earlier unmerged S0-E6D–E6I drafts already implement dormant RS256 issuer, exact Platform/Runtime source token interoperability, transactional one-time refresh exchange, real PostgreSQL refresh rotation and Identity HTTPS/SPIFFE tests. This does not recreate them. Runtime [draft #296](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/296) independently proves a signed HTTP-to-PostgreSQL review adapter, still using a DIFFERENT synthetic review contract.

Protected Platform and Runtime `main` are unchanged. Platform's historical master roadmap 34/34 complete is independent of this S0 work.

## S0-E6K implementation

1. `RuntimeAudienceTokenIssuer.verifyOwnSignedTokenForEnrollment` re-verifies the **actual Compact RS256 token** with the issuer-derived RSA public key and checks signed `kid`, issuer, *exactly one* Runtime audience, positive UID, canonical unique JTI, role/scope ceiling, issued-at/expiry and bounded five-minute lifetime. It returns an immutable six-field tuple **without bearer credentials**, and refuses malformed, forged, mismatched or expired tokens with generic error messages. Legacy HS256 login/refresh token issuance remains unchanged.
2. `RuntimePg16SessionEnrollment` is an unannotated, unregistered JDBC adapter which invokes the parent S0-E6J issuer-only `SECURITY DEFINER` `enroll_signed_session` function with the exact verified tuple and a fresh per-command UUID. It demands a writable READ COMMITTED transaction, uses `SET LOCAL ROLE e6j_issuer` in the **disposable CI database**, and resets the role. A false, absent or errored enrollment fails closed. It never inserts authority tables directly. It is NOT a production database-client authentication implementation.
3. `RuntimeAudienceAtomicRegistryExchange` is deliberately **not a Spring component or public HTTP route**. When an approved caller explicitly registers it as a Spring-managed transactional proxy using the same PostgreSQL physical datasource, its `@Transactional(rollbackFor=Exception.class)` method composes:
   - existing transactionally constrained one-time refresh rotation, persisted account reload and RS256 Runtime audience JWT issuance;
   - independent verification of the *just-created* signed token;
   - same-transaction issuer-role enrollment and audit recording;
   - database COMMIT **before the Spring proxy exposes** both result credentials.
   If signer/ledger/epoch/transaction fails, the refresh claim, replacement credential, enrollment and audit all roll back. No minted JWT is returned. The existing S0-E6H mTLS bridge is **not automatically switched** to this new candidate.

## Real disposable PostgreSQL evidence

**Initial complete code+test HEAD** `20c17b651754defbf230ad4f02b64ce457b98d05`: [GitHub Actions #42576768169](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42576768169) **COMPLETED SUCCESS, 2/2 jobs, 9 November 2026**:

- Parent S0-E6J real `RuntimeSessionLedgerPg16IT`: **6/6** passed, with separate issuer/reader/revoker privileges and SQL row-lock/revocation/audit acceptance.
- New `RuntimeAudienceAtomicRegistryExchangePg16IT`: **7/7** passed, actual Spring CGLIB transaction proxy, actual PostgreSQL16 JPA refresh credentials + function-only registration, two signed users, distinct JWT/JTI records, unregistered-JWT denial, epoch-missing and blocked-signer rollback, two-role live revocation checks, forged JWT rejection, six simultaneous one-time refresh claims resulting in exactly **one** committed access token + session and one audit row. Existing role-limited JDBC verifier is called through a separate connection and the same review-only ledger function. The disposable schema is removed and CI checks cleanup.
- Existing Identity/RS256/refresh/Gateway SPIFFE policy regressions: **32/32**, zero failure/error/skip. Production HMAC sessions, existing routes and HTTP access unchanged.
- Earlier workflow #42576663960 did **not** start a job due invalid YAML compact checkout mapping; corrected to valid explicit mapping. That run was NOT treated as an application test failure or passing evidence.
- New test and old test classes are explicitly run in a dedicated PG16 CI job, not bypassed via skipped tests. The full four-module reactor, real E6I HTTPS fixture, real Gateway network, live Runtime HTTP and physical owner PC are NOT re-certified by this focused CI.

### Critical security and release limitations

- Existing SQL functions **do not independently verify RSA tokens**. The trusted Identity issuer and the same-transaction private Java wrapper must be certified with separate workload trust/custody. A caller obtaining direct issuer-function credentials could submit invented claims. The disposable `SET LOCAL ROLE` lab is **not** proof of distinct network-authenticated production issuer credentials.
- There is no versioned approved production migration, restricted real SQL accounts, backup/restore/split-brain protection, deployment/secret/key lifecycle, or real session cleanup/retention policy. There is no new controller/filter/HTTP session exchange and no runtime task/mission route admission.
- Runtime [draft #296](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/296) uses separate `synthetic_session_registration` / `signer_key_states` / `principal_epochs` contracts, not these E6J `e6j_runtime_auth` SQL functions. **Two projects are not yet interoperable end to end at the database session authority boundary.**
- The existing E6H `RuntimeAudienceWorkloadBoundExchange` still calls the earlier internal exchange service; it is **not rerouted** through the opt-in enrolled transaction wrapper. No live Identity-user refresh browser flow, mTLS gateway route or external session exchange has been authorized.
- A successful session preflight alone never grants object ownership, current tenant/project membership, task/mission resource access, workload fence, safe per-owner event replay or SSE. PostgreSQL `FOR SHARE` locks are scoped to the check transaction, not the subsequent HTTP resource handler.

## Next new milestone: S0-E6L

Create the **independently reviewed Runtime-side function-only session authority adapter** against an exact pinned Platform E6J SQL/Java contract, then exercise *actual* Platform-issued RS256 JTI and real Runtime direct-port HTTP session checks with two independently persisted users against shared, disposable PostgreSQL16. Test positive registration, unsigned/HS tokens, unregistered JWT, different user, session/signing-key/epoch revocation races, outage and read-only privilege violations. Do not merge diverged draft stacks or unlock Task/Mission/SSE. Separately owner-approve production database migration and distinct network-authenticated issuer/verifier/revoker client credentials before any staging promotion.

**Runtime Security #23 OPEN; Platform Security #251 OPEN; Stage 45 NO_GO; XERJ HOLD; owner-PC BLOCKED_PENDING_HARDWARE.** No new Stage 35 or certification.
