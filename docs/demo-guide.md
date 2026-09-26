# Aetheris Demo Guide

This is the shortest reviewer-friendly path through Aetheris. It is deliberately **platform-first**: demonstrate the cloud-native backend, identity, messaging, observability, resilience and deployment story before discussing the separate Syntra/Aetheris AI-runtime track.

## 1. What this demo is meant to prove

A successful core demo can show:

- the platform-owned service topology starts locally;
- gateway, identity, user and audit surfaces are reachable;
- PostgreSQL, Redis and RabbitMQ responsibilities are explicit;
- authentication/token lifecycle is implemented;
- the dashboard is served;
- repository resilience/deployment choices can be explained and inspected.

The optional AI/operator track is owned by `teldigi5-wq/aetheris-ai-runtime`. It is not required to demonstrate the core portfolio value, and its hosted certification must not be confused with physical owner-PC validation.

## 2. Prerequisites

Recommended for the containerized demo:

- Git
- Docker with Docker Compose v2

For module-level work you may also need Java 21, Node and Python.

## 3. Clone and orient the reviewer

```bash
git clone https://github.com/teldigi5-wq/aetheris-platform.git
cd aetheris-platform
```

Open these first:

- `README.md`
- `docs/portfolio-scope.md`
- `docs/architecture-decisions.md`

The first message to a reviewer should be simple:

> Aetheris is primarily a distributed-systems/platform-engineering project. Its AI/orchestration runtime is separately owned and maturity-labeled rather than being presented as equally mature platform-local source.

## 4. Start the platform-owned core

Use the source-absent core Compose file when the goal is to demonstrate only platform-owned components:

```bash
docker compose -f docker-compose.core.yml up --build -d
```

In another terminal:

```bash
docker compose -f docker-compose.core.yml ps
```

Expected platform-owned local surfaces include:

| Surface | Address | Source owner |
|---|---|---|
| Dashboard | `http://localhost:3000` | `aetheris-platform` |
| Gateway | `http://localhost:8080` | `aetheris-platform` |
| User Service | `http://localhost:8081` | `aetheris-platform` |
| Identity Service | `http://localhost:8082` | `aetheris-platform` |
| Audit Service | `http://localhost:8083` | `aetheris-platform` |
| RabbitMQ management | `http://localhost:15672` | infrastructure |

The gateway keeps an orchestrator URI contract even in core-only mode, but the orchestrator implementation is not sourced from this repository.

Compose credentials and fallback secrets are development defaults only.

## 5. Demonstrate identity and token lifecycle

The identity service exposes:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
```

Explain these design points:

- access JWTs are short-lived authorization credentials;
- refresh tokens are credentials, not ordinary IDs;
- refresh-token rotation/revocation matters;
- persisted refresh credentials should be represented by hashes rather than reusable plaintext values;
- gateway enforcement does not remove the need for downstream authorization assumptions.

Do not paste real credentials or tokens into screenshots or public issues.

## 6. Demonstrate the distributed request path

Explain the platform-owned service flow:

```text
Client / Dashboard
  → API Gateway
  → platform downstream service
  → PostgreSQL / Redis as needed
  → RabbitMQ event when asynchronous work is appropriate
  → Audit consumer
  → metrics / logs / traces
```

When AI orchestration is required, the gateway crosses the versioned external-runtime boundary instead of calling a platform-local orchestrator source tree.

The important part is responsibility ownership. A strong demo explains why each dependency exists and what should happen if it fails.

## 7. Explain Redis and cache trade-offs

Use the code/configuration to discuss:

- what is safe to cache;
- staleness/invalidation trade-offs;
- which security decisions should not silently become permissive if Redis is unavailable;
- why caching is an optimization, not a source of truth for everything.

## 8. Explain RabbitMQ and asynchronous audit flow

Use the producer/consumer path to discuss:

- why audit/event processing can be decoupled from the synchronous request;
- delivery failure;
- duplicate delivery/idempotency;
- poison messages/retry strategy;
- why using a broker does not automatically solve reliability.

## 9. Enable observability

For the integrated demo, use the external-runtime composition after the certified runtime artifact/reference is available:

```bash
docker compose -f docker-compose.integration-external.yml --profile observability up -d
```

Additional surfaces include Grafana, Prometheus, Tempo and Loki. Explain the roles:

- **Prometheus** — metrics;
- **Grafana** — visualization/exploration;
- **Loki** — logs;
- **Tempo** — traces;
- **OpenTelemetry** — instrumentation and telemetry foundation.

A strong explanation connects telemetry to a real debugging question rather than simply listing the tools.

## 10. Explain resilience

Show the Resilience4j policies and explain why retry depends on side effects.

A useful interview contrast is:

```text
safe read fails transiently → a bounded retry may be acceptable
state-changing request fails ambiguously → blind retry may duplicate the mutation
```

Also explain how a circuit breaker reduces pressure on an unhealthy dependency and prevents cascading failure.

## 11. Show Kubernetes / Helm assets

Walk through the deployment assets and discuss:

- deployments and services;
- liveness/readiness;
- replicas;
- desired state;
- configuration through Helm;
- what happens when a pod is removed or becomes unhealthy.

The value is the operational reasoning, not simply the existence of YAML files.

## 12. Repository-quality controls

Show protected `main` and GitHub Actions. Useful platform checks include:

- backend/service tests;
- dashboard build;
- CodeQL;
- compatibility-contract freeze;
- platform/runtime source-independence checks;
- release/portfolio evidence checks.

For the optional AI runtime, show the independently owned runtime certification workflows in `teldigi5-wq/aetheris-ai-runtime` rather than implying those tests execute against platform-local runtime source.

## 13. Strong 10-minute interview sequence

### Minute 0–1 — Architecture

Gateway + service boundaries.

### Minute 1–3 — Identity/security

Registration/login/refresh/logout, token lifecycle, refresh-token hashing.

### Minute 3–5 — Distributed state and messaging

PostgreSQL + Redis + RabbitMQ + audit/event flow.

### Minute 5–7 — Observability/resilience

Metrics/logs/traces + circuit breaker/retry trade-offs.

### Minute 7–9 — Deployment

Docker Compose + Kubernetes + Helm + readiness/replicas.

### Minute 9–10 — Engineering quality

Protected `main`, CI, CodeQL and source-ownership boundaries.

Stop there unless the reviewer specifically wants the AI/operator work.

## 14. Optional extension demo — Syntra / Aetheris AI runtime

If the interviewer is interested in AI systems or automation, continue with the secondary track and explicitly switch repository context to:

`https://github.com/teldigi5-wq/aetheris-ai-runtime`

The platform currently records certified runtime checkpoint:

`65a6262717adcd52ac8d8a16ed6f223e299fd74d`

Explain the maturity split first:

- orchestration/policy code is runtime-repository tested;
- generic-browser/site-skill code exists in the runtime track;
- exact browser runtime, authenticated LinkedIn/Vercel sessions and target-PC behavior are **not physically validated yet**;
- live-money trading is disabled by policy.

Then show the runtime-owned orchestrator/workstation/reasoning/quant code and tests. Do not treat a newer runtime `main` revision as silently replacing the certified runtime checkpoint.

## 15. Physical validation boundary

**Physical-machine status: `BLOCKED_PENDING_HARDWARE`.**

Repository CI does not prove GPU acceleration, voice hardware, sustained thermals, local-model latency, browser sessions or workstation behavior on hardware that has not been tested.

The later master roadmap still records **Stage 34 / 34** for historical/contract reasons, but that is not the primary portfolio claim.

There is no production-activation, registry-publication or live-money execution claim from hosted repository evidence alone.

## 16. Failure-demo ideas

Useful controlled platform demos include:

```bash
docker compose -f docker-compose.core.yml stop user-service
```

Then use metrics/logs/traces and gateway behavior to explain how the failure is observed and contained.

Other useful exercises:

- stop RabbitMQ and inspect event-path behavior;
- stop Redis and discuss which behavior should degrade versus fail closed;
- remove a Kubernetes pod and inspect desired-state recovery;
- exercise an endpoint protected by resilience policy and explain why the retry behavior is safe or unsafe;
- with the external runtime intentionally unavailable, explain how the platform/runtime boundary fails without restoring a local duplicate runtime implementation.

## 17. Demo rule

Never hide a failure during a technical demo. If a component fails, use it as evidence that you understand diagnosis, dependency boundaries and recovery.
