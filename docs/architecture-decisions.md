# Architecture Decisions

This document records the reasoning behind important Aetheris design choices. The goal is to make the system easier to maintain and to make trade-offs explicit rather than hiding them inside implementation details.

## ADR-001 — Modular services instead of one large application

**Decision:** separate gateway, identity, user and audit responsibilities into focused services.

**Why:**
- Clear service boundaries make responsibilities easier to explain and test.
- Security-sensitive identity logic stays isolated from general user-domain logic.
- Individual services can evolve, scale and fail independently.

**Trade-off:** distributed systems introduce deployment, observability and failure-handling complexity that a monolith would avoid.

## ADR-002 — API gateway as the external entry point

**Decision:** route client traffic through a dedicated gateway.

**Why:**
- Central place for routing and cross-cutting policies.
- Keeps internal service topology out of client applications.
- Provides a natural boundary for authentication, rate limiting and resilience controls.

**Trade-off:** the gateway becomes an important dependency and must remain observable and resilient.

## ADR-003 — JWT + RBAC for authentication and authorization

**Decision:** use signed tokens and role-based access control for protected routes.

**Why:**
- Stateless access-token validation fits distributed services well.
- Roles/scopes make authorization rules explicit.
- Refresh-token handling supports longer-lived sessions without making access tokens long-lived.

**Trade-off:** token revocation and refresh-token security require careful lifecycle management.

## ADR-004 — PostgreSQL as the durable system of record

**Decision:** use PostgreSQL for persistent relational data.

**Why:**
- Strong consistency and transactional guarantees.
- Mature indexing, relational modeling and operational tooling.
- Good fit for identity, user and audit-domain data.

**Trade-off:** relational schemas require deliberate migration and data-model evolution.

## ADR-005 — Redis for fast ephemeral state

**Decision:** use Redis for caching and rate-limiting style workloads rather than as the primary database.

**Why:**
- Very low-latency access for short-lived state.
- Natural fit for counters, TTL-based entries and caches.
- Reduces pressure on primary persistence for repetitive reads.

**Trade-off:** cache invalidation and stale-data behavior must be designed explicitly.

## ADR-006 — RabbitMQ for asynchronous events

**Decision:** use a message broker for events that do not need to block request/response paths.

**Why:**
- Decouples producers from consumers.
- Enables audit/event processing without tightly coupling services.
- Provides a realistic environment for learning delivery guarantees and failure handling.

**Trade-off:** asynchronous systems are harder to debug and require idempotency, retry and observability discipline.

## ADR-007 — Observability is a first-class capability

**Decision:** treat metrics, logs and traces as part of the architecture rather than optional debugging tools.

**Why:**
- Distributed failures cannot be understood reliably from local logs alone.
- Metrics expose system health and trends.
- Tracing helps follow requests across service boundaries.

**Current direction:** Prometheus, Grafana, Loki, Tempo and OpenTelemetry.

**Trade-off:** observability adds infrastructure, storage and instrumentation overhead.

## ADR-008 — Resilience at service boundaries

**Decision:** use timeouts, retries, circuit breakers and health probes deliberately around remote dependencies.

**Why:**
- Network calls fail differently from in-process calls.
- Bounded retries and timeouts prevent indefinite resource consumption.
- Circuit breakers reduce cascading failures.

**Trade-off:** badly tuned resilience rules can amplify traffic or hide underlying faults.

## ADR-009 — Docker Compose for local integration, Kubernetes + Helm for orchestration

**Decision:** keep both a low-friction local integration path and a production-style orchestration path.

**Why:**
- Docker Compose is fast for local development and end-to-end checks.
- Kubernetes exposes scheduling, probes, service discovery, replicas and configuration management.
- Helm keeps deployment configuration reusable and parameterized.

**Trade-off:** maintaining two deployment paths requires discipline so they do not drift apart.

## ADR-010 — Stage the platform instead of adding everything at once

**Decision:** grow Aetheris in verified stages.

**Why:**
- Each capability can be understood and validated before the next one is added.
- Makes failures easier to isolate.
- Produces a clear engineering narrative for code reviews and interviews.

**Trade-off:** staged delivery can feel slower than adding many features quickly, but it produces better understanding and lower integration risk.

---

## How to use this file

When a major design choice changes, update the relevant ADR or add a new one with:

1. **Context** — what problem is being solved.
2. **Decision** — what approach was selected.
3. **Alternatives considered** — what else could have been used.
4. **Consequences** — benefits, costs and failure modes.
5. **Verification** — how the decision is validated in code or runtime behavior.

This document is intentionally concise. Implementation details should remain in the dedicated architecture, observability, resilience, security and Kubernetes documentation.