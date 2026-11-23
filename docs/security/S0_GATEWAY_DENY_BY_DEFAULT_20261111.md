# S0 — Gateway deny-by-default routes and actuator exposure (11 November 2026)

> **Rebuilt on #378 → #427 (v2 of #426).** #378's ADMIN-only rule for `/api/users` and `/api/events`, issuer/audience validation and verified-id rate-limit key are kept unchanged; this change replaces the protected-prefix list with a public allowlist, adds raw-path hardening and null-scope refusal, and locks down gateway actuator exposure.

**Status:** DRAFT review branch. Stage 45 NO_GO, owner PC `BLOCKED_PENDING_HARDWARE`, XERJ HOLD — unchanged.

## Problem

1. **Allow-by-default routing.** `JwtAuthenticationFilter.isProtectedRoute` listed three prefixes (`/api/users`, `/api/events`, `/api/orchestrator`). Any new gateway route was public unless someone remembered to add it, and an unlisted path under a protected prefix fell through to `users:read`.
2. **Ambiguous paths.** The filter decided on the decoded path; encoded dot segments, double slashes or matrix parameters could in principle be read differently by the filter, the route predicate and the backend.
3. **Actuator exposure.** The gateway exposed `prometheus`, `circuitbreakers` and `circuitbreakerevents` to every caller on port 8080, alongside `health` and `info`.

## Change

| Area | After |
|---|---|
| Public routes | Explicit allowlist: `/api/auth/` only. Every other routed request needs a valid bearer token. |
| Scope rules | Explicit per prefix (`/api/users`, `/api/events`, `/api/orchestrator`, exact or `/…` sub-paths). A routed path with **no rule is refused (403)** even with every scope. Look-alikes such as `/api/usersx` or `/api/authx` match nothing. |
| Path hardening | Raw path checked **first**: `//`, `/./`, `/../`, trailing `/.` or `/..`, `;`, backslash, `%2f`, `%5c`, `%2e`, `%3b`, `%25` → **400**. Query strings are unaffected. |
| Actuator | `include: ${AETHERIS_GATEWAY_ACTUATOR_EXPOSURE:health,info}`. Compose passes the same default. The observability and resilience proofs opt in to `prometheus,circuitbreakers,circuitbreakerevents`. Helm uses only health probes and is unaffected. |
| Docs | `observability/README.md`: how to opt in locally for Prometheus scraping. |

Note: actuator endpoints and `/fallback/*` are served by Spring WebFlux directly, not through gateway routes, so the JWT filter never saw them; exposure is controlled by the actuator setting above.

## Tests

- `JwtAuthenticationFilterTest`: `/api/auth/login` public; unknown routed path → 401 without token and **403 with a token holding every scope**; look-alike prefixes not public; 9 ambiguous paths → 400 and never forwarded; ordinary paths not flagged.
- `tools/security_runtime_smoke.py` + contract (hosted, real gateway): default stack returns **404** for `/actuator/prometheus`, `circuitbreakers`, `circuitbreakerevents`, `env`, `beans` and **200** for `/actuator/health`; encoded dot-segment and matrix paths refused.
- `tests/test_gateway_deny_by_default.py` (Build / release-hardening): allowlist only, null-scope refusal, raw-path check precedes routing decision, every routed prefix has a scope rule, actuator default `health,info` in service and compose.
- `tests/test_actuator_health_boundary.py`: assertion widened to accept the env form while still requiring health first.

## Evidence (before CI)

| Check | Result |
|---|---|
| Path logic compiled with javac 21 and probed | 10 ambiguous paths rejected, 4 normal paths allowed, look-alike prefixes not public |
| New boundary test | pass; fails on canonical code (4 failures, 1 error) as intended |
| All Python policy tests, Stage 22 release gate, Stage 23 contract guard | pass |
| Java unit tests, Docker runtime proofs | **not run locally** — this PR's exact-head CI is the first real run |

## Rollback

Revert the commit, or set `AETHERIS_GATEWAY_ACTUATOR_EXPOSURE=health,info,prometheus,circuitbreakers,circuitbreakerevents` to restore the old actuator exposure without reverting.
