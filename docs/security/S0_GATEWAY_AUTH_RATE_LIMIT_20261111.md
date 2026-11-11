# S0 — Gateway auth rate limit: no spoofable key, strict credential route (11 November 2026)

**Status:** DRAFT review branch. Stage 45 NO_GO, owner PC `BLOCKED_PENDING_HARDWARE`, XERJ HOLD — unchanged.

## Problem

1. **Spoofable rate-limit key.** `RateLimitConfig` keyed buckets on the `X-Aetheris-User-Id` request header. On protected routes the JWT filter overwrote it, but on `/api/auth/**` the client's own value reached the limiter. Sending a new value per request gave every request a fresh bucket, so login and registration were effectively unlimited. Spoofed identity headers were also forwarded to identity-service.
2. **Loose credential limit.** Login and registration shared the general identity limit (5/s, burst 10 — about 300 password guesses per minute per IP).

## Change

| Area | After |
|---|---|
| `JwtAuthenticationFilter` | Strips all `X-Aetheris-User-Id/Email/Role/Scopes` on **every** route, public included. After JWT verification, records the user id as an exchange attribute (`io.aetheris.gateway.verifiedUserId`) — never as a trusted inbound header. |
| `RateLimitConfig` | Per-user key only from that verified attribute; otherwise peer IP. Request headers are never read. |
| `application.yml` | New `identity-credentials` route, matched before `identity-service`, for `/api/auth/login`, `/register`, `/browser/login`, `/browser/register`: **1 request/s per IP, burst 10** (≈60/min sustained). Refresh/logout keep 5/s burst 10. |
| Performance proof | Setup registration slowed from 0.26 s to 1.05 s per account to stay within the new policy (42 accounts, ~45 s). The load profiles themselves are unchanged. |

The limiter is per peer IP as Docker/the host sees it; `X-Forwarded-For` is not trusted. Behind a shared reverse proxy all clients would share one bucket — revisit when a trusted proxy exists.

## Tests

- `RateLimitConfigTest`: verified attribute → `user:42`; header-only → IP; 50 rotating spoofed headers from one IP → one bucket.
- `JwtAuthenticationFilterTest`: spoofed identity headers removed on `/api/auth/login` (other headers kept); verified id exposed only as an attribute; rejected token sets no attribute.
- `tools/security_runtime_smoke.py` + contract: new hosted runtime check `security.auth-rate-limit-ignores-spoofed-identity` — 14 rapid failed logins with rotating forged headers against the real gateway and Redis must yield HTTP 429 (only 401/429 allowed).
- `tests/test_gateway_auth_rate_limit.py` (Build / release-hardening): resolver reads no headers, public routes strip identity headers, credential route strict and ordered first, every route has a limiter.

## Evidence (before CI)

| Check | Result |
|---|---|
| New boundary test | 4/4 pass; on canonical code fails (2 failures, 1 error) as intended |
| All Python policy tests, Stage 22 release gate, Stage 23 contract guard | pass |
| Java unit tests, Docker runtime proofs (incl. the new 429 check) | **not run locally** — this PR's exact-head CI is the first real run |

## Not addressed

- No per-account lockout: one attacker with many IPs is limited per IP only.
- Redis outage behaviour is unchanged (Spring Cloud Gateway's default).
- Remaining exposed-API items: allow-by-default gateway routes and actuator exposure.

## Rollback

Revert the commit. No data or schema changes.
