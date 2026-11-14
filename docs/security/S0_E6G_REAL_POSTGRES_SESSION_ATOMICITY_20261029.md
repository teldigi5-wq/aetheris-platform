# S0-E6G — real disposable PostgreSQL session atomicity and rollback

**29 October 2026 · Stage45 NO_GO · draft-only code · no actual owner/database access.**

## Independently verified base

- Direct parent [Platform draft PR #349](https://github.com/teldigi5-wq/aetheris-platform/pull/349) at immutable `dd1437b55092e18aa99b41874d36c9c6ebec8634`, [exact-head CI #40894341645](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40894341645) successful with **32/32 Platform Identity and 11/11 pinned Runtime tests**. Parent E6F introduced an *internal-only* refresh-token to RSA Runtime-token exchange service. No public token endpoint or Gateway caller.
- Runtime original HTTP denial independently pinned to `teldigi5-wq/aetheris-ai-runtime@c0618c827b326eb6a5bd02fa2a5e9365d593b4c6`. Its real ownerless Task/Mission HTTP filter and all-API/Actuator quarantine remain unchanged, draft/unmerged.

## What E6G specifically tests

This slice adds **real PostgreSQL transaction tests, CI, review documentation, and one narrowly scoped production-source correction**. The first test run exposed a real Spring integration defect: `RuntimeAudienceSessionExchangeService` was declared `final`, preventing Spring CGLIB from proxying its `@Transactional` exchange method. That class is now non-final so the actual transaction proxy can be created. No method body, routes, session defaults, production Hibernate policy or public-token issuance behavior is changed. A dedicated `AopUtils.isAopProxy/isCglibProxy` assertion requires genuine proxy registration before testing database effects.

`RuntimePostgresSessionExchangeAtomicityTest` uses the actual `IdentityAccount` and `RefreshToken` JPA entities, `IdentityAccountRepository`, `RefreshTokenRepository`, `RefreshTokenService`, production `RuntimeAudienceSessionExchangeService`, and real operator-configured `RuntimeAudienceTokenIssuer` with ephemeral, synthetic RSA keys. Unlike the E6F simulated repository, this test uses an actual independent **PostgreSQL 16 server** provisioned by GitHub Actions `services.postgres`, a disposable local JDBC database `aetheris_e6g_disposable`, a deliberately nonproduction username/password and no network calls to user infrastructure. The PostgreSQL service is run only on the isolated GitHub-hosted runner.

Destructive test safety: `@DynamicPropertySource` requires both `AETHERIS_E6G_DISPOSABLE_MARKER=ONLY_DISPOSABLE_E6G_POSTGRES` and an **exact local disposable PostgreSQL JDBC URL** before the test context constructs Hibernate. Otherwise this test **refuses to start**. `@BeforeEach` independently verifies the actual current database is `aetheris_e6g_disposable` and the actual server is PostgreSQL 16 **before** it removes disposable fixture rows. Hibernate `create-drop` is test-only; the real production Identity `application.yml` retains `ddl-auto: validate`.

Critical transaction guard: Spring `@DataJpaTest` usually starts a transaction around each test method and rolls it back at end. That would hide uncommitted inserted sessions from competing threads and make rollback claims meaningless. This class **explicitly overrides** that behavior using `@Transactional(propagation=NOT_SUPPORTED)`: the production `@Transactional` service uses its **real transaction manager and database commits** across independently executed calls.

Tests verify:

0. The **real Spring AOP/CGLIB transactional proxy exists** for the production exchange service (without the non-final class correction, the test application failed startup, so previous mock-only transactional success was not proof of real database transaction behavior).

1. A persisted PostgreSQL account's original refresh credential is atomically claimed, marked revoked, and a single replacement is committed; signed Runtime JWT reflects current database role and minimal audience scopes, and neither bearer credential leaks through result `toString`.
2. Existing revoked, expired and already-used refresh credentials cannot issue another RSA Runtime token; actual PostgreSQL revoked state is checked.
3. **Six real independent Java worker threads** attempt the identical refresh credential concurrently through real Spring proxies, not mocks. Exactly one worker may mint a Runtime token, exactly one replacement row may commit; the original is revoked. Strict result checks detect any unexpected DB/data-access/rollback error as test failure.
4. Signing failure after successfully claiming an original session must roll back the **real PostgreSQL transaction**: the refresh row's revoked flag and any replacement insert remain unchanged, and the same credential succeeds after only the synthetic malformed account data is fixed.

All existing Identity, browser, refresh, source-pinned Platform↔Runtime signature and JWT tests also run, and the pinned actual Runtime's verifier plus full Spring Boot/real HTTP quarantine regression must pass in the same immutable-head workflow. The audit prints high-level pass markers, no actual token/key values.

## What it does NOT certify

- PostgreSQL 16 disposable CI is stronger than mocked DB logic but is NOT deployment PostgreSQL, production schema upgrade/migration, RLS, backup/restore, production connection privileges or real multi-region/network race/load testing. It does not certify production application startup under `ddl-auto: validate`.
- There is **no external token-exchange API, mTLS workload authentication, per-user permission/tenant/task ownership, browser origin/CSRF session-exchange gate, Runtime user-session/`jti` revocation, or currently safe authenticated Runtime HTTP availability**. Already-issued RSA tokens remain valid until expiry even if refresh credentials are revoked. Original Runtime E4 blanket quarantine would block legitimate API and Actuator health traffic if deployed without an authorized availability plan.
- The parent Platform dashboard vulnerability audit **issue #240** remains open, independently blocking broad release readiness. Runtime issue [#23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23) and Platform issue [#251](https://github.com/teldigi5-wq/aetheris-platform/issues/251) remain OPEN. Other draft token stages are not yet merged or deployed.
- **E6 STILL IN PROGRESS**: six planned S0 engineering packages E6–E11 including ongoing E6 remain; 9/9 separate Stage45 release certifications are pending. Owner-PC physical verification remains BLOCKED_PENDING_HARDWARE. Production **Stage45 NO_GO**.

All work remains a stacked draft PR. **No protected-main merge, force push, runtime certified pin promotion, real user database access, public endpoint, customer token issuance, Task/Mission/SSE activation or production deployment.** Only a completed exact-head GitHub Actions SUCCESS can verify this slice.
