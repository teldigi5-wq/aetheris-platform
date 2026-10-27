# RS256 resource-server key independence — reviewed code candidate

**27 October 2026 · stacked on draft #337 · Stage45 NO_GO**

## Verified source gap

The previous RS256 option still required a shared HMAC signing secret:
Identity and Gateway checked a legacy secret before RSA configuration, and
User/Audit's protected-route guard returned 503 when the HMAC key was absent,
even if a valid RS256 public verifier existed. This prevented real signer-only
private-key custody and verifier-only public-key rollout without distributing
a historical symmetric secret.

## This draft's security boundary

- **Identity**: `aetheris.jwt.secret` injection defaults to empty. A valid
  reviewed `AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM`, together with
  valid issuer/audience settings, independently signs access and internal
  bounded-audience JWTs using **RS256 only**. It does NOT construct any
  HMAC key in RSA mode. Invalid/missing private-key PEM does not silently
  fall back to HMAC. The original HMAC-only path **still fails startup** if
  no >=32-character secret was configured.
- **Gateway**: `aetheris.jwt.secret` injection also defaults to empty.
  In verified pinned-public-key RSA mode the HMAC key is never initialized
  or read as a fallback, and real `GlobalFilter` checks keep signed issuer,
  audience and principal validations. Without a public key, an absent/short
  HMAC secret fails initialization as before.
- **User + Audit**: their real `OncePerRequestFilter` protected-route gates
  now check `(HMAC key present OR verified RS256 public key present)`.
  In configured RSA-only mode they deliberately set the HMAC key to null.
  Missing both or invalid issuer/audience policy still refuses protected
  requests (503); a mismatched or HMAC-signed token is rejected (401).
- **Tests**: actual JJWT signing, MockMvc servlet filters and reactive
  Gateway GlobalFilter run with empty/short/null HMAC settings, generated
  throwaway RSA 2048 keys and correct signed issuer + audience. They test
  RSA acceptance, HMAC downgrade rejection, and legacy missing-key refusal.
  Source tests also assert the RSA mode does not install an HMAC key.

Identity and Gateway also change their `application.yml` placeholders to `${AETHERIS_JWT_SECRET:}` so Spring can resolve an absent legacy secret; the constructors still enforce a >=32-character HMAC secret when RSA mode is NOT configured.

No RS256 key is committed; no production configuration change; no addition
of new HTTP endpoints, privileges, database schema, tenant mappings or
deployment. All prior #337 key-ID rotation protections remain enabled when
the matching key-ID configuration is supplied.

## What is NOT certified

This removes the shared-HMAC **technical dependency** for the opt-in RSA
mode, but it does NOT provision secrets, prove production private-key custody,
validate trusted signing environment and mTLS workloads, perform automatic
key rotation/revocation, certify tenant/object ownership or external
Stage45 release gates. The legacy symmetric signing implementation remains
for existing deployments unless separately approved and retired. Do not
delete real secrets, edit running workloads or activate RSA settings without
an owner-approved cutover and rollback plan.

**Issue #251 OPEN · Nine production attestations pending · Stage45 NO_GO.**
