# B62-A — Consistent clock source for verifier temporal policy

**26 October 2026 — REVIEW-ONLY STACKED DRAFT**, based on B61 PR #295 exact head `2964ebf68925f27d18412fb7f3b2ee88abfe712a`. Neither protected `main` nor production, real keys, sessions, volumes or deployed workloads are touched.

## Problem and change

B61 added an injected UTC `Clock` for old RS256 key retirement, but the Gateway administrative route and direct User/Audit filters still used independent calls to `Instant.now()` to validate signed `iat` and `exp` claims. In production these currently consult the same operating-system time source; nevertheless their separation makes deterministic boundary testing inconsistent and can produce different temporal decisions if clocks are injected for verifier tests.

B62-A replaces just those manual `Instant.now()` reads with `clock.instant()`. All currently configured production constructors continue to use `Clock.systemUTC()`. The 30-second future-`iat` tolerance and strict nonexpired `exp` comparison are intentionally unchanged. The JJWT signature, `iss`, exact singleton `aud`, `kid`, role, scope, signed UID and B61 per-request previous-key cutoff checks are preserved. This slice does **not** change token signer behavior or configure library-level clock skew.

## Verification contract

- Every changed API filter uses exactly one injected `Clock` for its own signed access-token time checks and B61 old-key cutoff.
- Each service's genuine synthetic Identity-issued RSA current/previous JWT suite varies a test clock within **the same verifier instance**: at 180 seconds behind, both tokens must reject as future-issued; on real-time alignment they must pass; the old key fails at the absolute retirement cutoff; the current key fails when the clock advances beyond its signed expiration.
- The dedicated B62-A exact-HEAD workflow runs the Python source guard, existing B61 contract guard, full four-module Java reactor and all three signed-token deadline integration suites. Hosted workflow passing would verify these scoped claims only. Do not represent it as a live-clock/NTP or real-device test.

## Non-goals and open blockers

This is an intentionally small slice, **not the whole B62 lifecycle policy**. A proposed maximum access-token lifetime, allowed cross-host skew, refresh-session compatibility during real key rollover, emergency private-key revocation, durable clock-source integrity and audited operator change control must still be designed, tested and approved. JJWT parses expiration using its own clock unless separately configured; B62-A aligns only the filter's explicit checks, and it does not claim a globally atomic timestamp across signature verification. No B62-A statement should imply irreversible old-key revocation.

Stage45 remains `NO_GO`; #242/#251/#255/#259 and all nine runtime release certification gates remain open. Never merge or deploy solely because synthetic CI is green.
