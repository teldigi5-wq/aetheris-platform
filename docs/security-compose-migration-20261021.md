# Aetheris Platform — Compose secret and loopback hardening (21 October 2026)

**Security remediation candidate; do not deploy to a shared host or merge until full CI and operator migration are reviewed.** No production activation or owner's PC validation is asserted.

## Confirmed issues in prior `main`
- `docker-compose.yml`, `docker-compose.core.yml`, and `docker-compose.integration-external.yml` mapped PostgreSQL (`5432`), Redis (`6379`), RabbitMQ AMQP (`5672`) and broker UI (`15672`) to all host interfaces by using plain `HOST:CONTAINER` mappings. User, identity, audit, orchestrator, gateway and dashboard also had host-wide binds; observability endpoints did too.
- All three entrypoints used the literal database/broker password `aetheris`; `docker-compose.yml`/external integration exposed a deterministic JWT fallback `change-me-local-development-secret-32chars` and observability used a deterministic Grafana admin password. Spring app defaults also silently selected database/broker/JWT development passwords.
- The optional Alloy observability service previously mounted the Docker control socket read-only; that did **not** restrict Docker API privileges. This branch removes the mount and replaces the Docker API collector with a **read-only, operator-fed log-file bridge**. Metrics and traces remain available. Continuous container log export now requires a separately controlled host-side exporter. Treat operator-exported logs as sensitive and protect retention/access. Verify Alloy and Loki behavior in exact-head CI before treating the replacement as certified.
- Other residual checks include Redis authentication/isolation on the internal Compose network, stronger service-to-service auth, TLS/reverse proxy for any external access, `SPRING_JPA_HIBERNATE_DDL_AUTO=update` on legacy services, image provenance/digest and vulnerability scans, access-token revocation and real rate-limit stress tests. No claim of exhaustive vulnerability closure.

## Remediation in this branch
1. Remove host publication entirely for PostgreSQL, Redis and RabbitMQ in all three entrypoints; service-to-service access stays on Docker's internal Compose network. Use `docker compose exec` for local maintenance.
2. Bind remaining developer HTTP/debug/telemetry ports to explicit `127.0.0.1`. A local process or SSH-tunneled session may access those ports; remote networks cannot connect directly via those host bindings. **Local loopback is not a substitute for host firewall or workload authorization**.
3. Set `AETHERIS_POSTGRES_PASSWORD`, `AETHERIS_RABBITMQ_PASSWORD`, `AETHERIS_JWT_SECRET`, `AETHERIS_GRAFANA_ADMIN_PASSWORD` to **required, nonempty environment variables**. No embedded Compose fallback; Spring app configs no longer default secret values. The browser refresh cookie defaults to Secure, not insecure HTTP.
4. CI runtime-proof workflows now generate **separate ephemeral random secrets per job**, with GitHub log masking and no committed or uploaded passwords.
5. A dedicated GitHub Actions job actually parses `docker compose config --format json` in default/core/integration modes, refuses missing secrets, checks network binding and performs a read-only sweep of all fetched remote branch Compose files. Historic branches remain marked risk-bearing until separately rebased and verified; scanning does not patch history.

## First-time local setup (Windows, Linux, macOS)
Make a private `.env` in the project root (ignored by Git), based on `.env.example`. Generate **four different** 32-byte random secrets using Python:

```sh
python -c "import secrets; print(secrets.token_hex(32))"
```

Use a separate output for each of the four required environment keys. **Never commit, screenshot, or paste secret values into a GitHub issue, PR, chat or CI log.** Keep a protected backup in an offline password manager. `docker compose config --format json` prints interpolated secrets, so do not paste its unredacted output into tickets. On Linux/macOS protect the file (for example `chmod 600 .env`). Keep developer preview on loopback. For a locally served HTTP-only browser-auth proof, `AETHERIS_BROWSER_REFRESH_COOKIE_SECURE=false` is an **explicit temporary loopback-only testing exception**, not a production default.

A blank or omitted password fails Compose **before startup**. All future internet-accessible deployment needs authenticated TLS ingress, verified non-loopback routing/firewalls, managed secrets, separate DB/broker identities and security review.

## Existing containers / volumes — critical migration, do not blindly restart

These environment changes **do not change an existing PostgreSQL role password or RabbitMQ stored password** if a named data volume already exists. **DO NOT use `docker compose down -v` on an existing installation**—it deletes named volumes and may destroy user data.

1. Arrange an operator-approved maintenance window, make/test backups, record current volumes/roles and stop writes.
2. Via an authenticated, restricted maintenance session rotate the *existing* PostgreSQL role password with `ALTER ROLE`, rotate RabbitMQ user's password with `rabbitmqctl change_password`, and separately update the secure secret store/.env. Avoid putting secrets in shell history; use interactive commands or a secret-managed channel. Do not expose old/new secrets in logs.
3. Rotate JWT signing keys deliberately; that invalidates existing signed access tokens and requires coordinated gateway/identity restart. Plan refresh-token and client sign-in behavior. Rotate Grafana administrator credentials as well.
4. Roll out one service tier at a time, perform authentication/authorization/rate-limit and recovery smoke tests, and verify only expected 127.0.0.1 ports remain. Do not run on the owner PC until the PC is available.
5. If any prior credentials were ever deployed on an internet-reachable host, treat them as potentially compromised and review logs/exposure; repo edits cannot retroactively remove published images/secrets from history or existing environments.

## Verification and scope
CI evidence certifies only exact-head source configuration and the GitHub runner environment. It does NOT prove physical host firewall state, remote port closure in an already-running stack, no secret compromise, absence of vulnerabilities in dependencies/branch history, or production compliance. A separate follow-up must address observability Docker socket and remaining service-to-service security boundaries.

**No forced merges, branch deletion, production changes, or physical-PC changes in this remediation.**

## Next security slice: Redis password and rate-limiter/cache boundary

The Redis database (gateway rate-limit state and user-service cache) now requires
authentication in the proposed Compose configuration. Operators must provide a
**new independent** `AETHERIS_REDIS_PASSWORD` (do not reuse JWT, PostgreSQL,
RabbitMQ, or Grafana secrets). The same value is injected into the Redis server,
Redis health/maintenance client through `REDISCLI_AUTH`, and Spring clients through
`SPRING_DATA_REDIS_PASSWORD`. Missing or empty password fails Compose config.

This is **not** a production rollout or credential rotation. Redis data in named
volumes is preserved. Before upgrading an existing installation, back up and
validate recovery, identify direct Redis users, coordinate all clients, and
apply the new credential in a reviewed maintenance window. An unauthenticated
client will intentionally stop working; do not weaken authentication.
Never use `docker compose down -v` on an existing environment.

For local Compose, `REDISCLI_AUTH` is inherited by `docker compose exec redis redis-cli`
so diagnostic commands remain authenticated. CI proves anonymous `redis-cli ping`
receives NOAUTH. Do not print unredacted `docker compose config`, Docker process
arguments, or secret values. Docker administrators can view container environment
and launch arguments; use dedicated secret facilities and TLS/ACL/workload
isolation beyond trusted local development.

**Scope:** Three Docker Compose variants only. Legacy Helm chart values, historical
branch drift, service-to-service authorization, and Redis network segmentation
need separate review. CI cannot certify an existing workstation or deployment.
