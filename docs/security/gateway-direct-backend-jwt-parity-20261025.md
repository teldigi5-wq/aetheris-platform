# Gateway ↔ direct-backend JWT trust-boundary parity

**Draft security verification, 25 October 2026.** Stacked on backend PR #269 and earlier service-auth PRs #268/#267. No deployments, live accounts, protected-main merges or production key changes.

## Verified design mismatch before this change

Identity `JwtService` uses a shared HMAC signing key and issues a signed JWT containing subject, positive integer `uid`, `role`, `scopes`, `iat` and `exp`. Its `Role` policy intentionally grants `users:read` and `services:read` to API_CONSUMER/DEVELOPER, whereas the independent User and Audit service filters are currently **ADMIN-only** for all `/api/users[/**]` and `/api/events[/**]` routes.

Prior Gateway `JwtAuthenticationFilter` checked matching scopes, but **not ADMIN** for those administrative backend routes, and used `Number.longValue()` for its verified rate-limit identity. Also `getFirst(Authorization)` disregarded a second Authorization header, while backend filters required exactly one bearer value. This caused inconsistent gateway acceptance vs backend rejection and may allow ambiguous authorization handling at intermediaries.

## Narrow fail-closed fix

For User and Audit administrative API paths only, after verifying the original signed JWT:

- Require exactly **one** Bearer Authorization header, bounded to 4096 characters; reject whitespace and malformed tokens.
- Require nonblank subject and role, a positive **Integer or Long** JWT `uid`, an `issuedAt` no more than 30 seconds in the future and a nonexpired `expiration` timestamp.
- Require signed role `ADMIN` **and** the matching method scope (`users:read/users:write` or `services:read` under current Gateway routing). Invalid identity/lifetime yields **401**; valid identity with wrong administrative role or scope yields **403**.
- Put the verified rate-limit user ID and any forwarded `X-Aetheris-User-Id` metadata into an outbound request only when the signed `uid` is a positive whole-number `Integer`/`Long`. The Gateway never reads client-supplied `X-Aetheris-*` identity metadata for authorization and continues overwriting it for protected routes.
- Preserve Orchestrator's separate signed-token/role-scopes behavior. An `API_CONSUMER` with `orchestrator:read` may still use the Orchestrator read route, without acquiring ADMIN capability on User/Audit. Non-integral Orchestrator ID must not set a verified user-ID limiter bucket or forwarded user-ID metadata.

**Caveat:** This does not add ownership/tenant isolation to User rows, audience/issuer enforcement, real JWT revocation or a service-workload identity. It does not create an audit write API, and the Gateway's event route currently requires `services:read` for all HTTP methods, whereas Audit service requires `services:write` for mutations; this policy discrepancy requires a separate reviewed contract and does not grant permission to mutate.

## Verification

`.github/workflows/gateway-backend-jwt-parity.yml` runs on this stacked PR against both Ubuntu 24.04 and Windows Server 2022:

- Full Gateway Maven suite (JWT, route configuration, rate-limiting behavior, resilience tests).
- User and Audit direct JWT MockMvc tests and registered random-port HTTP tests.
- Existing `tests/test_backend_jwt_wiring.py` deployment-secret and forwarded-header source invariants.

New Gateway unit regressions cover duplicate bearer values, signed non-admin users on both administrative route families, nonpositive/fractional/missing identity and missing/future issuance, a valid ADMIN scope, rate-limiter user ID integrity, and continued API_CONSUMER Orchestrator read routing.

CI success proves consistency of the specified **code and test boundaries**, not a live shared Gateway-to-Identity-to-User-to-Audit Docker deployment. Physical PC, separate container/Pod network probes, real issued-account compatibility and ownership remain open under #251. Stage45 stays NO_GO; the nine enterprise release-certification gates are not closed.

**No need to weaken direct backend filters or user scope semantics to make this test pass.**
