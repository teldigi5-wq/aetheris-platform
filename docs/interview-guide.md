# Aetheris Interview Guide

This guide is for explaining Aetheris accurately in a technical interview. Lead with the **cloud-native platform engineering** because it is the strongest, most legible part of the project. Treat the later Syntra/Aetheris control-plane work as an optional extension topic.

## 30-second explanation

Aetheris is a distributed-systems and platform-engineering project built with Spring Boot. It has an API gateway, identity/user/audit services, PostgreSQL, Redis, RabbitMQ, Resilience4j, a React dashboard, OpenTelemetry with Prometheus/Grafana/Loki/Tempo, and Docker/Kubernetes/Helm deployment assets. The project is designed to demonstrate secure service boundaries, token lifecycle, asynchronous messaging, observability, resilience and cloud-native operations.

After that core platform was established, I added a separate experimental orchestration/control-plane track for local AI and automation. I discuss that second track only when it is relevant to the role because it has different maturity and physical-validation requirements.

## 2-minute explanation

The core architecture starts with a Spring Cloud Gateway and independently bounded identity, user and audit services. PostgreSQL provides durable relational state. Redis supports distributed low-latency concerns such as caching/rate-related controls. RabbitMQ carries asynchronous events so audit/event work does not have to block every synchronous request.

The identity design treats refresh tokens as credentials. Access tokens are short-lived JWTs, while refresh-token state can be rotated/revoked and stored as hashes rather than reusable plaintext credentials.

The platform includes observability with OpenTelemetry, Prometheus, Grafana, Loki and Tempo, plus bounded resilience behavior through Resilience4j. Retry is not applied blindly: safe reads and mutating operations have different risk characteristics.

For deployment, Docker Compose gives a practical local path while Kubernetes and Helm demonstrate health/readiness, replicas and cloud-native operations.

A later research track adds Syntra/Aetheris orchestration, deterministic approvals, generic-browser foundations and other pre-PC automation work. I keep that separate from the main platform claim so I do not confuse repository-tested research with physically validated production capability.

## Recommended interview order

1. Gateway and service boundaries.
2. Authentication and refresh-token lifecycle.
3. PostgreSQL / Redis / RabbitMQ responsibilities.
4. Failure handling and Resilience4j decisions.
5. Metrics, logs and traces.
6. Docker / Kubernetes / Helm deployment.
7. CI, CodeQL and reproducibility.
8. Optional: Syntra/Aetheris control-plane research.

See [`portfolio-scope.md`](portfolio-scope.md) for the maturity split.

## Architecture questions

### Why use an API gateway?

Clients get one stable ingress rather than depending directly on every internal service. The gateway is a natural place for routing and cross-cutting concerns such as CORS, request correlation and traffic policy. The trade-off is that it becomes critical infrastructure, so it must be observable and resilient.

### Why keep services separate instead of one large application?

The separation is used where responsibilities and trust differ: identity/token lifecycle, user-domain logic, asynchronous audit/event handling and orchestration do not have identical persistence, scaling or security concerns. The point is explicit boundaries, not “microservices for the sake of microservices.”

### Why PostgreSQL?

Identity and user state benefit from transactions, constraints and a mature relational model. It makes consistency rules explicit and is easy to reason about in an interview or production-like design discussion.

### Why Redis?

Redis provides low-latency distributed state for concerns such as caching and rate-related controls. A key design rule is that Redis failure must not silently weaken authorization or security policy.

### Why RabbitMQ?

Audit/event work can be decoupled from the synchronous request path. A message broker makes asynchronous delivery, retry/failure and consumer boundaries visible instead of hiding them inside one request transaction.

### Why DTOs instead of returning persistence entities?

Persistence models and public contracts change for different reasons. DTOs reduce accidental field exposure, provide a validation boundary and prevent API clients from coupling directly to JPA/database structure.

## Security and identity questions

### How does authentication work?

The identity service exposes registration, login, refresh and logout flows. Access JWTs represent short-lived authorization context. Refresh tokens are credentials and therefore require explicit rotation/revocation/validation behavior rather than being treated like harmless IDs.

### Why hash refresh tokens in storage?

A stolen database should not automatically provide reusable refresh credentials. Storing a hash reduces the impact of persistence compromise compared with plaintext reusable tokens.

### Why not trust the gateway completely?

The gateway is an important enforcement point, but downstream services should preserve their authorization assumptions. A routing mistake or compromised ingress should not turn every internal service into an unguarded trust zone.

### What is your secret-management position?

Repository examples and Compose defaults are development-only. A real deployment requires external secret management, TLS and hardened environment-specific configuration. Secrets must not be committed, logged or copied into evidence artifacts.

## Caching and consistency questions

### Why cache at all?

Caching reduces repeated expensive reads and lowers latency, but it introduces invalidation and staleness trade-offs. The important design question is which data can safely be stale and which authorization/security decisions should never depend on an unsafe cache fallback.

### What happens when Redis is down?

The system should expose the dependency failure rather than silently weakening a security boundary. Degradation strategies depend on the specific cache use case; authorization should not become permissive just because cache infrastructure failed.

## Messaging questions

### Why asynchronous audit events?

Audit persistence often should not extend user-facing latency unnecessarily. Asynchronous events separate the user request from downstream audit processing while preserving an explicit operational trail.

### What failure modes do you think about with RabbitMQ?

Producer failure, consumer failure, duplicate delivery, poison messages, retry policy, ordering assumptions and idempotency. The correct handling depends on the business event rather than assuming “the broker makes it reliable automatically.”

## Reliability questions

### Why Resilience4j?

It gives explicit circuit-breaker/retry/time-limiter patterns in the Java/Spring ecosystem and makes failure policy visible in code/configuration.

### Why not retry every failed request?

A retry can duplicate a mutation. Repeating a safe read and repeating “create order” are not equivalent. Retry policy should consider idempotency and side effects rather than only HTTP status codes.

### What does a circuit breaker buy you?

It limits repeated calls to an unhealthy dependency, reduces cascading failure pressure and creates an explicit recovery state rather than allowing every request to block/fail identically.

## Observability questions

### Why Prometheus, Grafana, Loki, Tempo and OpenTelemetry?

They represent complementary observability dimensions: metrics, visualization, logs and traces. OpenTelemetry provides a standard instrumentation/telemetry foundation so distributed requests can be followed across services.

### Is observability proof that a user action succeeded?

No. Telemetry is evidence about system behavior. Business-level success still requires the application to define and verify the expected outcome.

## DevOps and deployment questions

### Why both Docker Compose and Kubernetes/Helm?

Compose provides a fast local integration path. Kubernetes/Helm demonstrate health, replicas, declarative deployment and configuration concepts. Supporting both keeps development practical while still demonstrating cloud-native design.

### What does Kubernetes add to the portfolio story?

It lets you discuss readiness/liveness, desired state, replicas, service discovery, rollout behavior and recovery when a pod disappears — not just packaging an application into a container.

### What does reproducible build evidence add?

The repository uses deterministic dependency/lock checks and a two-pass build comparison. That improves confidence that the same source/dependency inputs produce the same expected artifacts. It is still repository evidence, not proof of a future physical workstation environment.

### Why is `main` protected?

Stable history is protected by the `Protect stable main` ruleset. Changes are promoted through pull requests and configured checks rather than being pushed directly into stable history.

## Optional AI / orchestration questions

### What is the difference between Syntra and Aetheris?

Syntra is the owner-facing assistant/coordination experience. Aetheris is the infrastructure/control layer behind it. In the experimental extension track, models can propose actions but deterministic policy, approvals and verification remain separate from model output.

### Why not lead the interview with this part?

The cloud-native platform is easier to verify and directly relevant to more backend/platform roles. The AI/operator track has useful engineering work, but several capabilities still depend on future physical runtime and external-site validation. Keeping the tracks separate is more credible than presenting every research idea as one equally mature product surface.

### Does the generic browser already update LinkedIn or deploy through Vercel on the target PC?

No. Repository-side browser and site-skill implementations exist, but exact selectors, authenticated sessions and physical Chrome/Edge/WebDriver behavior remain `BLOCKED_PENDING_HARDWARE` until tested on the target machine.

### Does Aetheris autonomously trade real money?

No. The quant/trading area is a fail-closed research/risk foundation. Live-money execution, withdrawals and transfers are outside the default trusted AI path.

## About the 34-stage roadmap

The later Syntra × Aetheris roadmap reached **Stage 34 / 34** at the repository-contract level. Do not use that number as the opening portfolio pitch.

A better explanation is:

> “The original cloud-native platform is the primary portfolio project. I later used the repository as a research platform for orchestration/governance work. That second roadmap completed its repository-side specification, but some capabilities still require physical validation.”

This keeps the history truthful without letting stage-count vocabulary overshadow concrete engineering.

## Strong 10-minute demo sequence

### Minute 0–1 — Problem and architecture

Explain the gateway and service boundaries.

### Minute 1–3 — Identity

Show registration/login/refresh/logout and explain token lifecycle and refresh-token hashing.

### Minute 3–5 — Distributed data flow

Explain PostgreSQL, Redis and RabbitMQ responsibilities and one asynchronous audit/event path.

### Minute 5–7 — Observability and resilience

Show metrics/logs/traces and explain one failure scenario plus retry/circuit-breaker decisions.

### Minute 7–9 — Deployment

Show Compose, Kubernetes/Helm assets, readiness/replicas and how the system recovers from a service/pod failure.

### Minute 9–10 — Engineering quality

Show protected `main`, CodeQL/reproducibility checks and architecture decisions.

Only if the interviewer is interested, continue into the Syntra/Aetheris control-plane extension.

## Things not to claim

Do not say any of the following unless new evidence is added later:

- “The full system is production-ready.”
- “Every 34-stage capability is equally mature.”
- “The physical target PC is validated.”
- “LinkedIn/Vercel browser automation is already physically validated.”
- “The AI has unrestricted administrator control.”
- “Aetheris autonomously trades live money.”
- “Hosted CI proves GPU, voice or thermal performance.”

## Closing answer: what did you learn?

A strong answer is that the project taught you to reason about boundaries: service ownership, credentials, asynchronous failure, cache consistency, safe retry, observability and deployment state. The later control-plane work reinforced a second lesson — implementation, verification and operational maturity are different claims and should be labeled separately.
