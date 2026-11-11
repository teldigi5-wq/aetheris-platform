# S0 — Compose host ports bound to loopback only (11 November 2026)

**Status:** DRAFT review branch. Stage 45 NO_GO, physical owner PC `BLOCKED_PENDING_HARDWARE`, XERJ HOLD — all unchanged. This change closes one local exposure; it is not a production-readiness claim.

## Problem

Every Docker Compose file published host ports in the short form `"8081:8081"`. Docker binds that to `0.0.0.0` (all interfaces) and inserts its own iptables rules, so host firewalls such as `ufw` do not block it. On any machine running the stack, anyone on the same network could reach:

| Port | Service | Why it matters |
|---|---|---|
| 8081 | user-service | No authentication of its own; trusts the gateway. `DELETE /api/users/{id}` worked directly. |
| 8083 | audit-service | No authentication of its own; returns user names and emails in events. |
| 8082 | identity-service | Login/registration reachable without the gateway's rate limiter. |
| 8090 | orchestrator-service | Runtime API reachable without the gateway's JWT/scope checks. |
| 5432 / 6379 / 5672 / 15672 | Postgres / Redis / RabbitMQ / RabbitMQ UI | Demo credentials `aetheris/aetheris`; Redis has no password. |
| 3001 | Grafana (observability profile) | Demo admin credentials. |

The S0-E6N review already listed Docker port exposure as an open production prerequisite.

## Change

- `docker-compose.yml`, `docker-compose.core.yml`, `docker-compose.integration-external.yml`: all 43 host mappings now use `127.0.0.1:` (e.g. `"127.0.0.1:8081:8081"`). Container-to-container traffic on the compose network is unchanged.
- `tests/test_compose_loopback_ports.py`: fails if any tracked compose file publishes a port beyond loopback (short or long syntax, wildcard `0.0.0.0`, or a variable host). Includes negative tests proving the detector flags each unsafe form. Wired into the `Build / release-hardening` job.
- `scripts/first_boot_preflight.py`: the existing port contract now counts only loopback-bound mappings, so a regression to `0.0.0.0` shows up as missing ports instead of passing.

## What still works

Every runtime-proof workflow and local smoke test calls services on `localhost`, which loopback binding still serves. `http://localhost:8080` (gateway) and `http://localhost:3000` (dashboard) still work in the browser on the same machine.

## What changes for you

To open the dashboard or gateway from **another device** (phone, second PC), add an untracked local override such as `docker-compose.lan.local.yml` that rebinds only those two ports, and only on a trusted network. Never commit it, and never rebind the internal services or infrastructure.

## Evidence (this branch)

| Check | Result |
|---|---|
| `python -m unittest tests.test_compose_loopback_ports` | 7/7 passed locally (Python 3.13) |
| Same test against `main`'s compose files | fails, listing every all-interface mapping |
| `python -m unittest tests.test_first_boot_preflight` with new compose | passed locally |
| Same preflight against `main`'s `docker-compose.core.yml` | fails `platform-compose-port-contract` as intended |
| All 15 repository Python policy tests | passed locally |
| Java build, Docker runtime proofs | **not run locally** (no Maven/Docker network here) — rely on this PR's exact-head CI |

## Not addressed here (next, separate PRs)

1. JWT signing secret falls back to the public `change-me-local-development-secret-32chars` when unset — fail closed instead.
2. Self-registered `API_CONSUMER` accounts can list every user and read audit events.
3. Login rate-limit key can be spoofed via `X-Aetheris-User-Id` on `/api/auth/**`.
4. Gateway route protection is allow-by-default; actuator exposes `prometheus` and circuit-breaker endpoints.

## Rollback

Revert this PR's single commit. No data, schema or secret changes are involved.
