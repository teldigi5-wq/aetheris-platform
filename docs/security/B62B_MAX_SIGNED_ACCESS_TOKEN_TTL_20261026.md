# B62-B — Verifier-side maximum signed access-token validity

**26 October 2026 — REVIEW DRAFT**, stacked on verified B62-A PR #296 exact head `22f53d9d5c111c5f53e8a7e65ca206e20c844f9c`. No main merge, customer secrets or production/deployment modifications.

## Risk

A JWT whose signer-selected validity `exp - iat` is longer than one hour can be accepted by B61/B62-A despite its old-key overlap being limited to one hour. The signer-selected JWT lifetime and key rollover overlap are separate security controls. Checking only how many seconds remain to expiration would incorrectly allow an overlong token after it ages.

## Policy

Gateway (all protected API routes), direct User and direct Audit now require `iat` and `exp` with `exp > iat` and a **hard signed validity limit of 3600 seconds**. Both HMAC and RS256 verification paths use the shared result. Old/current RSA public-key identity binding, B61 absolute previous-key cutoff, issuer, exclusive audience, role/scope and UID validation continue to apply. Requests more than 30 seconds ahead in issuance time or with already-expired tokens remain denied by the existing B62-A rules. No new configuration or dynamically selectable clock is exposed to callers.

## Evidence target

In the existing Identity-only synthetic key generator, produce current and previous 1200-second RSA JWTs plus signed current-key `3600`, `3601` and `0` second fixtures. All three independent service filter integration tests verify that 1200 and exactly 3600 seconds are accepted, 3601 and 0 seconds rejected. Never export Identity private keys from the test JVM. Dedicated CI pins the PR HEAD, runs Python source policy guards, full Java reactor and real signed-JWT unit/integration tests. Scope the success claim strictly to the observed exact HEAD and workflow conclusion.

## Open safety boundary

This slice does not change the issuer's configuration (default access-token expiry is currently 3600s), perform actual session refresh/migration, certify JJWT parsing under injected clocks, validate NTP drift across hosts, implement emergency key revocation or replay all prior Docker HTTP tests. No live user/agent authorization, historical database restore, production keys, or actual rollout is certified. Stage45 NO_GO, #242/#251/#255/#259 and nine runtime release gates remain outstanding.
