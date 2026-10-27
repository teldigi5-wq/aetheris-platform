# Security #251 — Gateway actual signed-principal hardening candidate

**27 October 2026 · Draft stacked on #334 · protected main unchanged · NO_GO**

## Confirmed discrepancy

The preexisting Gateway `JwtAuthenticationFilter` validated HMAC signature,
optional issuer/audience, and required route scopes, but (unlike User and
Audit's newer servlet filters) did not independently enforce the signed
principal's numeric positive `uid`, known role name, typed/bounded scopes,
role-scope ceiling or `iat` validity. In particular, legacy `API_CONSUMER`
claims include `users:read` and `services:read`, but the actual direct User
and Audit service filters intentionally DENY `API_CONSUMER` on these APIs
until the owner/tenant/object binding is approved.

## Implemented change in the REAL Gateway GlobalFilter

1. Verify signed token as before (legacy HMAC; optional exact issuer and sole
   audience from parent #333). Before creating forwarding identity headers,
   require a nonblank bounded/trimmed subject, signed `uid` of Java numeric
   Long/Integer type with positive value, a known signed role
   (ADMIN/DEVELOPER/API_CONSUMER), `iat`, `exp` with `exp > iat` and
   `iat <= now+30s`, and bounded nonempty typed string-array scopes.
2. Restrict scopes in the signed token to the exact role-scopes ceiling
   currently declared by Identity `Role.java`. Unknown roles, string
   rather than list scopes, malformed scopes, and unauthorized elevated scopes
   are rejected **401**.
3. The Gateway refuses `API_CONSUMER` direct User and Audit routes **403**
   in line with both actual backend policies. It retains authorized
   `API_CONSUMER` `orchestrator:read` access only where permitted by that
   JWT's signed scopes. ADMIN/DEVELOPER User/Audit endpoints remain permitted
   where signed scopes allow. This is not object-level authorization.
4. Reject empty, oversized or multiple bearer headers without forwarding.
   Continue stripping user-provided identity/tenant headers before forwarding.

## Regressions

A new `GatewayPrincipalBoundaryTest` exercises the actual Gateway
`GlobalFilter` with independently signed synthetic JWTs: permitted
consumer Orchestrator read and privileged User/Audit read, consumer
User/Audit refusal, bad role/role escalation, nonnumeric/negative UID,
missing/future/expired time claims, missing/mixed/string/oversized scopes,
and spoofed header removal. The original signed-header-forwarding test was
updated to use the permitted consumer Orchestrator read route rather than
a User route that the real backend denies.

This requires exact-head hosted four-module Maven/Java tests, existing
disposable PostgreSQL 16 role isolation, synthetic backup/restore, audit
concurrent Flyway proof, and intentional Stage45 NO_GO. Do NOT count these
as end-to-end app deploy or historic tenant migration evidence.

## Rollout constraints

This changes behavior for signed but nonconforming legacy JWTs and direct
API_CONSUMER User/Audit Gateway calls (which were already blocked by
downstream direct filters in parent candidate). Review any genuinely
authenticated machine clients before promotion. Existing asymmetric issuer
key separation, true tenant/object authorization, immutable key custody,
replay/revocation, and E2E Gateway-to-backend HTTP proof remain open.
No secret/privilege migration, production database, compose/Helm config,
owner PC, deployment or protected-main merge is made.

**Issue #251 OPEN · 9 release attestations unresolved · Stage45 NO_GO.**
