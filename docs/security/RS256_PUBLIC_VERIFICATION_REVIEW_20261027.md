# Review-only RS256 signing / verification across four services

**27 October 2026 · Stack on #335 · NOT deployed · Stage45 NO_GO**

## Real code, no operational rollout

This review candidate introduces opt-in asymmetric JWT signing/verification to
the actual Identity, Gateway, User, and Audit Java authentication classes.

- Identity: AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM
  - PKCS#8 RSA private key is parsed on boot; RS256 signs the existing login
    access token and the bounded internal (non-HTTP) audience-specific mint.
  - A nonempty/malformed/weak key aborts construction; RS256 configuration
    requires an existing valid issuer and audience. No HS fallback.
  - Existing HMAC issuance remains only when the RSA setting is UNSET.
- Gateway: AETHERIS_GATEWAY_JWT_RS256_PUBLIC_KEY_PEM
  - X.509 RSA public key is parsed on boot. RS256 **only** when configured:
    a legacy HMAC token or wrong RSA signer is refused (401) even if an old
    HMAC secret remains present for compatibility.
  - The configured public key forces issuer/audience strict routing checks:
    incomplete route settings return 503, rather than silently reverting.
- User/Audit: AETHERIS_BACKEND_JWT_RS256_PUBLIC_KEY_PEM
  - Their real OncePerRequestFilter classes verify only RS256 against the
    configured public key. Missing issuer/audience policy returns 503.
    Existing signed role/scope restrictions remain, including API_CONSUMER
    denial until authentic tenant/resource binding is certified.

The PEM parsers accept only PKCS#8 PRIVATE KEY and X.509 PUBLIC KEY formats,
requiring RSA modulus 2048..8192 bits; no remote URLs/JWKS, no untrusted kid,
and no executable caller-selected algorithms. Malformed keys never print
their contents or cause signature-algorithm fallback.

Each of the four modules has executable tests using locally generated throwaway
RSA keypairs, adversarial wrong signing keys, HS→RS confusion attempts,
weak/malformed key refusal, route issuer/audience checks, and default legacy
backward compatibility. These are synthetic tests, NOT external trust certification.

## Why owner approval and further work remain mandatory

No production key, secret store, deployment environment, Docker image, route,
database migration, historical volume or protected main branch was altered.
No token issuer is actually switched to RS256 in a running deployment.
The real existing legacy HMAC secret is still configured in the code path
for compatibility, but it is never a verification/signing fallback once
RS256 is selected. Prior approved sessions with HMAC JWTs will be refused
by any RS256-only verifier: a synchronized canary, key inventory,
operator-approved cutover and rollback (including revocation/short expiry)
are essential.

Real private key custody in a trusted Identity signing environment,
key IDs / trusted rotation registry, issuer/audience consistency,
authenticated token exchange, transport-layer workload identity,
revocation, actual cross-service running HTTP QA, tenant/object grants,
and nine independent release attestations are still OPEN. No temporary
test keys are persisted; tests must generate ephemeral keys each run.
Never treat this PR as a deploy authorization or proof of key rotation.

**Issue #251 OPEN · Stage45 NO_GO · Draft/unmerged and synthetic QA only.**
