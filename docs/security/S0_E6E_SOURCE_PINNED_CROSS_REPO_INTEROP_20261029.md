# S0-E6E — exact-source Platform ↔ Runtime RSA token interoperability

**29 October 2026 — Stage45 NO_GO, DRAFT ONLY. No production user token exchange, API unlock or deployment.**

## Exact reviewed parents

- Platform Identity source: [S0-E6D DRAFT PR #347](https://github.com/teldigi5-wq/aetheris-platform/pull/347), exact parent `dc86cf89b1f642c58ad4f6e090d0b02ca15d7d9f`, targeted to the canonical Platform development branch `feature/syntra-aetheris-foundation-v2` in compliance with Stage27; its [focused Identity CI #40890606580](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40890606580) succeeded 19/19 tests.
- Runtime verifier source: independently pinned **public GitHub repository** `teldigi5-wq/aetheris-ai-runtime` at immutable [S0-E6C DRAFT PR #224](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/224) head `c0618c827b326eb6a5bd02fa2a5e9365d593b4c6`; its [CI #40887740615](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40887740615) passed 49/49 full-app/JWT/HTTP lockdown tests.
- Neither candidate is on protected `main`. The Platform currently issues legacy shared-secret HS256 tokens without the new runtime-specific `kid/iss/aud`; those cannot authenticate with the Runtime pinned RS256 verifier.

## Exact-source verification, no vendored/copied production implementation

The dedicated [`s0-e6e-platform-runtime-interop.yml`](../../.github/workflows/s0-e6e-platform-runtime-interop.yml) CI workflow checks out the exact Platform PR head and separately checks out the **exact Runtime SHA above**, using the pinned `actions/checkout` action with no persisted credentials and no secrets. Both repos are publicly readable. The workflow verifies both `git rev-parse HEAD` values and source guards BEFORE attempting any test.

For an isolated CI test compilation **only**, it copies the actual Runtime repository's `RuntimePinnedPrincipalVerifier.java` into Platform Identity's `src/test/java` tree and compares bytes with `cmp`. The cross-repo JUnit fixture is retained in `docs/security/ci/RuntimePlatformRuntimeCrossRepoRoundTripTest.java` and copied into `src/test/java` **only inside the disposable runner**. Platform's actual `RuntimeAudienceTokenIssuer` remains compiled from its own production source. No Runtime source is reimplemented or permanently vendored in Platform, no package is published or dependency edge is introduced.

The **six new cross-source unit tests** verify:

1. The real Platform issuer creates correctly signed RS256 tokens accepted by the real Runtime verifier for `ADMIN`, `DEVELOPER` and `API_CONSUMER`, matching UID, subject, role and exact role-bounded Runtime scopes.
2. Exact signed `kid`, issuer, **one** Runtime audience and five-minute expiry, with a deliberately advanced verifier clock rejecting expiry.
3. Foreign public keys, swapped signing keys, wrong `kid`, wrong issuer/audience and malformed bearer envelopes cannot authenticate.
4. Even correctly signed tokens with API consumer write-scope escalation or multiple audiences are rejected.
5. The real existing Platform legacy `JwtService`'s HS256 token is rejected by the actual Runtime RS256 verifier (no implicit interoperability).
6. With absent/incomplete operator signing parameters, the dormant Platform runtime-specific issuer cannot issue any token.

The workflow also runs the existing Platform Identity/Browser/Refresh/Issuer unit tests and the exact-pinned Runtime `RuntimePinnedPrincipalVerifierTest` and `RuntimeRealDirectPortDenialRegressionTest` to ensure the original Task/Mission 404 and real all-`/api`/`/actuator` 404 quarantine still function. The test uses ephemeral RSA private keys and fake account identities; no operator or customer private signing key enters GitHub logs, Git history or artifacts. The workflow uses only read-only GitHub checkout and Java builds.

## Truthfulness and strict release blockers

**This validates interoperability between the two actual production source implementations** in an isolated source-pinned test runtime. It does NOT prove a running Platform Identity service can exchange a token through an authenticated, nonrevoked session, nor that the running Runtime HTTP service will accept it. No real authenticated token-exchange endpoint exists, and the Runtime verifier is intentionally not connected to an HTTP resource server or data authorization.

**E6 remains IN PROGRESS.** Before even considering route-specific access: validate browser login/refresh session + revocation, credential/key rotation and `kid` pin rollover, authenticated mTLS Gateway-to-Runtime workload identity and direct-port rejection, verifiable user/tenant/project/object data binding, safe PostgreSQL/Flyway migrations, multiuser Task/Mission/SSE denial cases, and independent operational rollout/rollback. The Runtime S0-E4 blanket 404 would currently block legitimate APIs and actuator health and is not approved for deployment. Existing Platform unrelated [dashboard npm audit issue #240](https://github.com/teldigi5-wq/aetheris-platform/issues/240) also blocks full Platform release readiness, regardless of whether this focused contract CI passes.

Both Runtime [issue #23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23) and Platform [issue #251](https://github.com/teldigi5-wq/aetheris-platform/issues/251) remain OPEN. Historical 34/34 blueprint completion is not production capability; configured agent roles are not autonomous running processes. **Six S0 engineering packages E6–E11 remain including ongoing E6. All nine Stage45 release gates remain NOT CERTIFIED, owner PC physical test is BLOCKED_PENDING_HARDWARE; Stage45 NO_GO.**

Do not merge protected main, force-push, publish operator credentials, activate customer token exchange, relax Task/Mission HTTP 404, global SSE or API quarantine, promote runtime certification pin, or deploy production. **Only a completed, exact-head successful GitHub Actions run can certify this test milestone.**
