# Aetheris Identity — disabled-by-default audience-specific token minting review

**27 October 2026 · issue #251 partial · draft/unmerged · Stage45 NO_GO**

## Why this work matters

The existing Identity login/refresh returns ONE access token. PR #333 introduced
opt-in fixed-audience issuance and Gateway's route-by-route target validation,
but enabling a token intended for a single backend breaks legitimate use of
other backend routes. Issuing a token with ALL audiences would create a
cross-service replay vulnerability. A secure internal exchange is needed, but
creating a public `/token/exchange` without verified client/workload principal,
account lifetime and revocation would create a privileged token minting oracle.

## This draft, intentionally non-exposed

The actual Identity `JwtService` has a **package-private** method
`issueForApprovedAudience(IdentityAccount, ApprovedTokenAudience)`,
off by default behind
`AETHERIS_IDENTITY_TARGETED_TOKENS_ENABLED:false`. The existing
`/api/auth/login`, `/register`, `/refresh`, `/logout`, browser auth, and
`JwtService.issue()` are NOT changed.

- Even the internal method refuses to mint unless Identity issuer and primary
  audience are BOTH configured, and the separate targeted switch is enabled;
  partial/whitespace configuration fails Identity bean construction.
- `ApprovedTokenAudience` is a fixed typed enum with **User**, **Audit** and
  **Orchestrator**, matching the documented Gateway and backend route targets.
  It accepts no arbitrary strings, origins, URLs or request headers.
- It denies `API_CONSUMER` **regardless of their legacy scopes**, and denies
  missing/invalid identity, target or scopes.
- For ADMIN/DEVELOPER it intersects the original account role's scopes with
  the intended backend's allowed scope family, never grants new privileges:
  User `users:read/users:write`, Audit `services:read`, Orchestrator
  `orchestrator:read/orchestrator:write`.
- HMAC-signed target tokens have exactly ONE audience and the configured issuer.
  TTL is capped at `min(existingAccessTtl,300 seconds)`. No `tenant_id` claim
  is invented and no existing API consumer receives object access.
- Five executable JUnit tests invoke the **real JwtService** and assert all
  three distinct audience values, scoped claims, role denials, default-off gate,
  malformed operator settings, short TTL and no tenant claim. Hosted CI must
  pass exact-head source guard, full Maven reactor and inherited PostgreSQL
  role/ownership/Flyway rehearsals; release remains NO_GO.

## Explicit authorization/operational blockers

This method is NOT a public token exchange and MUST NOT be wired into
`AuthController` or any arbitrary caller without separate, tested
authentication. Merely having access to `IdentityAccount` does not establish
that a specific client is entitled to exchange a token. A real design needs
trusted workload identity, client-bound authentication, issuer/audience/role
validation, revocation and replay controls, time-limited delegation,
tenant/object grants, complete HTTP integration, audit and owner approval.
The shared HMAC secret still permits any component possessing it to mint
tokens; independent issuer signing keys / trust anchors remain open.

This draft is **not** a production privilege expansion or migration:
no route, real credential, database, deployed configuration, protected main,
live financial action or owner-PC hardware setting is changed. Keep
issue #251 OPEN, nine release attestations blocked and Stage45 NO_GO.
