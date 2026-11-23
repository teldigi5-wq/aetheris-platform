# B62-C — Pin JWT parser time to the verifier's trusted UTC clock

**26 October 2026 — REVIEW-ONLY DRAFT** stacked on B62-B PR #297 at exact head `9b2b6184f399d0027be2b5d5209098e0fc9bbb13`. No production, protected main, real credentials or persistent-data changes.

## Finding

B62-A/B62-B use an injectable Java `Clock` to check signed `iat`/`exp` and B61 old-RSA-public-key deadlines, but the JJWT 0.12.6 parser independently uses its default JVM/system-time source when validating JWT `exp` and `nbf`. A clock-controlled verifier can therefore disagree with its cryptographic parser about whether a signed credential is valid. This matters to reproducible negative tests, clock skew reasoning, and explicit defense-in-depth.

## Implemented correction

Gateway, User and Audit now bind **all three JJWT parsing paths** (current RS256 key, previous RS256 key, and legacy HMAC) to `clock(() -> Date.from(clock.instant()))` with `clockSkewSeconds(0)` before the existing pinned `verifyWith`, signed issuer/audience enforcement and `parseSignedClaims`. The production clock remains `Clock.systemUTC()`. The existing manual 30-second future-`iat` tolerance is unchanged; it is **not** a JJWT expiration/`nbf` skew allowance. JWT signatures, exact audience/kid binding, old-key cutoff and hard 3600-second signer lifetime remain mandatory.

## Reproducible evidence

The test-only Identity issuer creates an additional genuine RS256 JWT with a signed `nbf` 300 seconds after its issuance. Both the JWT and an ASCII epoch marker stay in an ignored synthetic test target folder; private RSA material remains in the test JVM. In each Gateway/User/Audit integration test, the very same filter with an adjustable clock must reject one second before `nbf`, accept at `nbf`, and reject after signed `exp`—without waiting for wall time to pass. Python guards also assert every parser branch has the injected clock and zero library-level skew. A dedicated exact-head hosted workflow runs the full four-module Maven reactor and the genuine signed-JWT integration suites.

## Remaining work and limits

No real host NTP/clock trust is certified. No authenticated operator session migration, previous-signer private-key destruction, durable revocation, rollback procedure, live workloads or actual Docker HTTP replay is performed. The Identity issuer still permits abnormal positive and nonpositive expirations in test configurations used to generate negative fixtures; a production fail-closed issuer-side guard and fixture separation remain a separate follow-on. Do not treat hosted CI as Stage45 approval. Nine runtime release gates and platform issues #242/#251/#255/#259 remain open. This change is compatible with JJWT 0.12.6's documented `JwtParserBuilder.clock(Clock)` and `clockSkewSeconds(long)` APIs.
