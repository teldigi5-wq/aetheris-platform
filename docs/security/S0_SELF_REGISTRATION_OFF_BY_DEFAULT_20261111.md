# S0 — Self-registration off by default (11 November 2026)

**Status:** DRAFT review branch. Stage 45 NO_GO, owner PC `BLOCKED_PENDING_HARDWARE`, XERJ HOLD — unchanged.

## Problem

`POST /api/auth/register` (and `/api/auth/browser/register`) let anyone create an account. Every self-registered account gets `API_CONSUMER` with `users:read`, `services:read` and `orchestrator:read`, so a stranger could register and immediately list every user's name and email (`GET /api/users`) and read the audit stream (`GET /api/events`).

## Owner decision (11 Nov)

Close it with a default-off switch now, keeping the existing role design, CI proofs and dashboard behaviour. A least-privilege redesign of `API_CONSUMER` (with an admin bootstrap) remains possible later.

## Change

- `IdentityService`: new `aetheris.self-registration.enabled` (env `AETHERIS_SELF_REGISTRATION_ENABLED`), **default `false`**. Checked before any storage or token work.
- `AuthController`, `BrowserAuthController`: disabled registration → **403** `Self-registration is disabled` (no-store on the browser path).
- Compose (3 files): passes the flag with default `false`. Helm sets nothing, so Spring's default (`false`) applies.
- 8 runtime-proof workflows that register synthetic accounts opt in explicitly with `"true"`.
- README: how to enable it for a local demo.

Login, refresh and logout are unchanged for existing accounts.

## Tests

- `IdentityServiceSecurityTest`: registration refused by default with no repository/JWT/refresh interaction; login still works when registration is off; existing tests run with the flag on.
- `tests/test_self_registration_default_off.py` (Build / release-hardening): service and compose default off, flag checked before persistence, both endpoints map to 403, every workflow that runs a registering tool opts in, nothing enables it by default.

## Evidence (before CI)

| Check | Result |
|---|---|
| New Python boundary test | pass; fails on canonical code as intended |
| All Python policy tests, Stage 22 release gate | pass |
| Java compile/unit tests, Docker runtime proofs | **not run locally** — this PR's exact-head CI is the first real run |

## Not addressed

`API_CONSUMER` still reads all users and events once an operator enables sign-up. Remaining exposed-API work: spoofable auth rate-limit key, allow-by-default gateway routes and actuator exposure.

## Rollback

Revert the commit. Or, without reverting, set `AETHERIS_SELF_REGISTRATION_ENABLED=true` to restore the old behaviour.

## Integration note (I5, 12 Nov 2026)

This change was ported onto the B62-F integration line. #424's own branch is unchanged.

On that line, User and Audit are ADMIN-only at the gateway and in each service, and the I4 role trim leaves API_CONSUMER with `orchestrator:read` only. Disabling sign-up is therefore defense in depth, not the only barrier to global reads.

Conflict choices kept the B-line's stricter Compose defaults: a required `AETHERIS_JWT_SECRET` and a secure refresh cookie by default. Only the `AETHERIS_SELF_REGISTRATION_ENABLED` lines were added. Proof workflows opt in, and keep per-run random secrets.
