# B62-D — Fail-closed Identity access-token lifetime configuration

**2026-10-26 | Review-only draft**, based on B62-C PR #298 exact successful head `1da01ce0ebc07bb843f38748f1976f6b9c3f247f`. No protected `main`, production secrets, real user sessions or deployment changes.

## Finding

Gateway, User and Audit verifiers reject signed access JWTs valid for more than 3,600 seconds (B62-B) but the production Identity `JwtService` constructor still accepts negative, zero and arbitrarily long `AETHERIS_JWT_EXPIRATION_SECONDS` values. An operator could issue tokens that consistently fail all API verifiers or exceed intended policy. Previously, such invalid configurations were used within synthetic test token-generation routines.

## Change

`JwtService` now fail-closes during construction unless `1 <= expirationSeconds <= 3600` regardless of HMAC/RS256 signing mode; it preserves the existing 3,600-second default, issuer/audience checks and private-key constraints, without allowing a production test escape hatch. Misconfigured services should fail startup rather than issue unusable JWTs. Unlike refresh tokens, access JWTs are covered by this bound; refresh session lifetime is a separate policy.

Three existing security fixture generators preserve their **signed, intentionally invalid** tokens with test-only JJWT builders in test source (and only synthetic signing material): B59 expired RS256, B60/B62-B overlong/zero-life RS256, and direct-auth expired HMAC. Valid token fixtures continue to use production `JwtService`. No production class is permitted to emit invalid signed test tokens.

## Verification

The new issuer unit test asserts invalid -120, -1, 0, 3601 and Long.MAX_VALUE are rejected; 1 and exactly 3600 are allowed, and a genuinely issued HMAC token has a signed 3600-second `iat`→`exp` window. Dedicated exact-head CI runs Python source controls, the full four-module Java reactor, previously real Identity-signed HMAC direct-verifier negative cases, RSA B59 negative fixtures, and B60/B61/B62 strict-signed RSA verifier suites with test-only signing keys.

**Limitations:** This does not certify live session or refresh rollover, issuer/consumer rollout order, real clock/NTP infrastructure, revocation, old RSA private-key rotation, physical Windows/WSL2/GPU tests, or post-change Docker HTTP behavior. Prior invalid production configuration would now fail at startup and requires rollout planning before promotion. Stage45 `NO_GO`, open #242/#251/#255/#259 and nine runtime release gates remain unresolved.
