# Aetheris Core Platform — Portfolio Case Study

## One-sentence summary

Aetheris is a cloud-native platform-engineering project that demonstrates secure service boundaries, token lifecycle design, distributed state, asynchronous messaging, resilience, observability and Kubernetes/Helm deployment in one coherent system.

## The problem

A common portfolio weakness is showing isolated technologies without demonstrating how they interact under security, failure and operational constraints. Aetheris was built to answer a more useful question:

> How would I design a small but credible platform where authentication, service boundaries, cache/state, asynchronous events, resilience, observability and deployment all have explicit responsibilities?

The project therefore emphasizes architectural reasoning and failure behavior rather than simply maximizing the number of frameworks used.

## Core architecture

```text
Client / React dashboard
        ↓
Spring Cloud Gateway
        ↓
Identity ── User ── Audit ── Orchestrator
   ↓          ↓        ↑
Postgres    Redis   RabbitMQ
        \      |      /
         OpenTelemetry
              ↓
Prometheus / Grafana / Loki / Tempo
              ↓
       Docker / Helm / Kubernetes
```

The optional Syntra/Aetheris AI-control-plane work is a secondary research track. A reviewer can ignore it entirely and still evaluate the platform-engineering project on its own merits.

## Design decisions I can defend

### 1. Gateway as a cross-cutting boundary

The gateway centralizes routing and cross-cutting traffic controls while downstream services retain their own responsibilities. It also provides a clear place for rate limiting, circuit breaking, request correlation and route-level resilience policy.

**Trade-off:** the gateway becomes critical infrastructure, so failure visibility and health monitoring matter.

### 2. Refresh tokens are credentials, not identifiers

The identity service issues random refresh tokens but stores only their SHA-256 hashes. Rotation revokes the old token before issuing a replacement, and logout/revocation invalidates stored token state.

**Why it matters:** a database leak should not trivially expose reusable plaintext refresh credentials.

**Trade-off:** stateful refresh-token lifecycle adds persistence and revocation complexity compared with completely stateless authentication.

### 3. Redis is used for ephemeral low-latency concerns

Redis supports cache/rate-related concerns rather than becoming the authoritative system of record. Durable user/identity state remains relational.

**Trade-off:** dependency failure must not silently weaken security or make authorization permissive.

### 4. Asynchronous events decouple audit/event work

User-domain events are published through RabbitMQ so every synchronous request does not have to block on downstream audit processing.

**Trade-off:** asynchronous delivery introduces eventual consistency, delivery/retry questions and observability requirements.

### 5. Retry only when repetition is safe

The gateway has separate read and write routes. GET/HEAD requests can receive bounded retries for selected infrastructure failures; mutating user requests do not get the same blind retry behavior.

**Why it matters:** resilience mechanisms can create duplicate writes if applied without considering idempotency.

### 6. Observability is part of the architecture

Prometheus, Grafana, Loki, Tempo and OpenTelemetry are treated as one diagnostic surface rather than optional decoration.

**Trade-off:** telemetry is evidence about system behavior, not proof that a business action succeeded correctly.

### 7. Compose for local integration, Helm/Kubernetes for orchestration

Docker Compose keeps local development accessible. Helm/Kubernetes assets demonstrate health, replicas, deployment topology and orchestration concepts without making Kubernetes mandatory for every developer loop.

## Evidence, not slogans

Every core claim in this case study maps to `build-evidence/portfolio/core-platform-evidence.json` and can be checked with:

```bash
python tools/validate_portfolio_evidence.py
```

The validator is intentionally fail-closed: a missing source path or required marker causes failure. It also reports:

```text
runtime_claim = NOT_EVALUATED
physical_pc_status = BLOCKED_PENDING_HARDWARE
```

That separation matters. Repository evidence can prove that a design and its tests/configuration exist; it cannot prove production load, real external users, every Kubernetes environment, or future physical-PC behavior.

## What I would demo in an interview

### Five-minute version

1. Show the gateway routes and explain read-vs-write retry policy.
2. Open `RefreshTokenService` and explain hash-at-rest, rotation and revocation.
3. Show `UserEventPublisher` and the asynchronous audit/event boundary.
4. Show the observability directory and one service's metrics/tracing configuration.
5. Show the Helm chart plus the evidence validator/CI result.

### Ten-to-fifteen-minute version

Add:

- identity-service tests;
- Redis cache configuration;
- a controlled circuit-breaker failure demo when a local runtime is available;
- `helm lint` / `helm template`;
- the protected-main and reproducibility checks.

## What I would not claim

I would not claim that:

- this is a production-proven platform;
- the project has meaningful external adoption because of GitHub stars/forks;
- hosted CI proves physical workstation behavior;
- every AI/operator template is operational on a real authenticated account;
- Kubernetes manifests imply validation on every cluster;
- observability configuration alone proves an SLO.

## What I learned

The strongest lesson from Aetheris is that adding a technology is easy compared with defining its responsibility and failure boundary.

The most valuable engineering questions became:

- What state is authoritative?
- Which requests are safe to retry?
- What happens when Redis or RabbitMQ is unavailable?
- Which credentials can be revoked?
- How do I distinguish configuration from runtime proof?
- How can another engineer verify a claim without trusting the README?

That is the story this repository should present to recruiters before discussing the later AI/control-plane research.
