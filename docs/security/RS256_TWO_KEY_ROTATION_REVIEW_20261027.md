# RS256 two-key pinned rotation — security review

**27 October 2026 · stacked draft after #336 · release Stage45 NO_GO**

## Review-only implementation

Identity optionally emits `kid` in signed RS256 JWT headers, from
`AETHERIS_IDENTITY_JWT_RS256_KEY_ID`. A configured key ID must match
`[A-Za-z0-9][A-Za-z0-9._-]{0,63}` and requires a valid private key; an
orphan/invalid key ID makes startup fail, never falls back to HMAC.

Gateway takes `AETHERIS_GATEWAY_JWT_RS256_KEY_ID`,
`AETHERIS_GATEWAY_JWT_RS256_STAGED_PUBLIC_KEY_PEM`,
`AETHERIS_GATEWAY_JWT_RS256_STAGED_KEY_ID`; User and Audit have the
corresponding `AETHERIS_BACKEND_JWT_RS256_*` values.

The current active public key is still supplied via the prior
`...RS256_PUBLIC_KEY_PEM` variable. An optional staged second X.509 RSA
public key allows overlap. Staging a second key requires a distinct, valid
pinned `kid` on both keys; partial/duplicate ID configuration, duplicate
public keys and weak/bad PEM abort service construction.

The verifier tries **only two preconfigured trusted RSA keys**, never
downloads `jku`, `jwk`, JWKS URLs, uses untrusted `kid` for key retrieval,
or accepts caller-selected JWT algorithms. After actual signature validation,
the JWT header `kid` must match the key that verified the signature.
Mismatched/unknown/absent IDs, a different RSA signing key and HS→RS confusion
attempts are refused.

An existing one-key RS256 configuration without `kid` remains supported
when NO rotation IDs are configured. Adding an active ID opts into explicit
`kid` checking. With staged key configured, no unsigned or missing `kid`
ever becomes an authorized verifier selection. Removing the former RSA
public key and its ID makes old tokens fail immediately.

Java tests (fresh ephemeral generated keys; no persisted test private keys)
exercise both public-key choices, spoofed `kid`, deliberate old-key
retirement, partial configuration, duplicate key or ID, bad signer and
HMAC downgrade. Tests execute the real Gateway, User and Audit filters and
the real Identity token issuer. Exact-head hosted CI additionally runs the
full four-module Java reactor and the inherited real disposable PostgreSQL
six-role migrations, synthetic backup/restore and Stage45 NO_GO gate.

## Required real-world owner actions before activation

This code **does not** automatically rotate cryptographic material:
the operator controls private key custody, published pinned public keys,
known-good overlap intervals, propagation checks, grace periods, revocation
response and emergency rollback. No trusted key registry, hardware-backed
private-key management, automated key distribution, compromised-key
monitoring, real TLS/mTLS workload identity, actual live distributed HTTP
acceptance or external key-control audit was executed.

Tokens signed before configured `kid` requirements are introduced WILL
FAIL the new verifier; enabling this while older services remain in the
fleet causes service availability risk. Plan an operator-approved staged
issuer/verifier rollout with complete traffic simulations before activating.
Do not assume withdrawing a signer alone invalidates previously issued
JWTs still accepted by an existing verifier. Key retirement requires
verifier removal at every resource service.

No production secret, real key, migration, physical device, environment,
protected main or deployed service is touched by this PR. The legacy HMAC
secret remains part of the *unconfigured* compatibility mode only; it is
never consulted after the RS256 public verifier is enabled.

**Issue #251 OPEN · all nine Stage45 external attestations outstanding · NO_GO.**
