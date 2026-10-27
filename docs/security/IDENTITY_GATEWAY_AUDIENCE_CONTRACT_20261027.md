# Identity → Gateway → User/Audit JWT audience contract — review only

**27 October 2026 · Draft and unmerged · Issue #251 partial hardening · Stage45 NO_GO**

## Actual code verified in source

The original Identity `JwtService` emitted `sub`, `uid`, `name`, `role`,
`scopes`, `iat` and `exp` under one shared-HMAC key, without `iss` or
`aud`. Gateway validated signature and scopes but did not distinguish the
target resource server. User and Audit direct filters in parent PR #332
already gained opt-in strict issuer/audience validation.

## This draft adds source-side and gateway-side opt-in checks

**Identity:**
- `AETHERIS_IDENTITY_JWT_ISSUER` and `AETHERIS_IDENTITY_JWT_AUDIENCE`
- When **both unset/empty**, keep existing legacy token shape, to avoid a
  surprise breaking change to current test and local development workflows.
- When both valid, mint a *single-audience* signed JWT with `iss` and an
  audience set containing one value only.
- One configured but not the other, whitespace-only values, or malformed
  target settings: fail service construction (never emit untargeted JWT).
- No tenant claim, implicit User.id == Identity.uid or new API privilege.

**Gateway:**
- `AETHERIS_GATEWAY_JWT_ISSUER`
- `AETHERIS_GATEWAY_JWT_USERS_AUDIENCE`
- `AETHERIS_GATEWAY_JWT_AUDIT_AUDIENCE`
- `AETHERIS_GATEWAY_JWT_ORCHESTRATOR_AUDIENCE`
- If all empty/unset, preserve existing Gateway verification semantics.
- If any is specified, require a valid issuer. A protected route whose
  audience is missing/invalid fails **503**, not legacy fallback.
- Only accept a token signed by the existing expected key with exact `iss`
  and **exactly one** `aud` equal to the destination service: otherwise
  **401**. All existing scope tests remain in force.
- Strip caller-supplied `X-Aetheris-Tenant-Id` from authenticated requests.
  No trusted tenant header or tenancy claim is created.

## Genuine test boundaries

Independent Java tests run the **real Identity issuer** and the **real Gateway
GlobalFilter**, checking one-recipient issuance, route-specific acceptance,
cross-service bearer replay refusal, missing/malformed/multi-audience and
incorrect issuer denial, spoofed tenant-header sanitization, incomplete
configuration fail-closed and original untargeted-token compatibility.
The existing User/Audit real servlet policy tests from #332 remain mandatory.

**This is not yet an end-to-end all-services deployment proof.** The Identity
auth endpoint currently issues one general access token per login; a fixed
single-audience issuance configuration cannot securely provide independent
User, Audit and Orchestrator tokens from one login. A real rollout needs
approved audience-specific mint/token exchange and signed trusted role,
subject and tenure/tenant ownership semantics, independent asymmetric key
custody, rotation, lifetime and revocation policy, and full signed multi-service
HTTP contract tests. Gateway's new check is opt-in, and operators must **not**
activate it on a real deployment until the receiving services and issuer
have migrated together with a canary, downgrade policy, backup/rollback and
owner/DBA authorization.

Never configure a wildcard or multi-audience JWT to paper over those
remaining design questions. Never use source-only green CI or environment
switches as release approval. No Compose/Helm credentials, volume,
persisted schema, physical owner device, deployment or protected-main merge
is changed. **Issue #251 OPEN, nine production attestations BLOCKED, Stage45
NO_GO.**
