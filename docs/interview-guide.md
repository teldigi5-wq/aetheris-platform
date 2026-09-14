# Aetheris Interview Guide

This guide is for explaining Aetheris accurately in a technical interview. The goal is to understand the design and trade-offs well enough to defend them, not to memorize buzzwords.

## 30-second explanation

Aetheris is a local-first platform engineering project that combines a Spring-based distributed backend, identity and audit services, Redis and RabbitMQ, a React dashboard, observability, Kubernetes/Helm deployment assets and an AI orchestration/governance layer. Syntra is the owner-facing assistant experience; Aetheris is the infrastructure and control plane behind it. AI models can recommend actions, but deterministic owner policy, risk rules, scoped approvals and verification remain authoritative. The repository roadmap is 34/34 complete, while physical-PC validation is intentionally still pending.

## 2-minute explanation

I built Aetheris as one evolving platform rather than a set of unrelated portfolio demos. Requests enter through an API gateway and are handled by independently bounded services such as identity, user and audit. PostgreSQL provides durable relational state, Redis supports distributed state/rate-related concerns, and RabbitMQ supports asynchronous event flow. The platform can run locally with Docker Compose, has Kubernetes/Helm deployment assets, and includes Prometheus/Grafana/Loki/Tempo/OpenTelemetry observability.

On top of the service platform I added an orchestration and governance layer for local AI/automation. The important design choice is that models are workers, not policy authority. Actions move through an explicit lifecycle: understand, plan, check rules, assess risk, preview or simulate when needed, get scoped approval when needed, execute, verify, record and report. A preflight `ALLOW` only means an action is eligible to run; it is not proof that the action executed successfully.

The repo also contains reasoning/verification work, digital-twin foundations, PC-care diagnostics, automation/emergency control and a fail-closed trading-intelligence foundation. I deliberately keep physical-machine claims separate from hosted CI, so WSL2, GPU, voice, thermals and real local-model performance remain `BLOCKED_PENDING_HARDWARE` until tested on the target PC.

## What makes the project technically interesting?

Aetheris connects multiple engineering disciplines in one coherent system:

- secure API and identity boundaries;
- distributed state and asynchronous messaging;
- observability and resilience;
- reproducible builds and dependency lockdown;
- local-first AI orchestration;
- deterministic safety/governance;
- deployment automation;
- explicit evidence and truth boundaries.

The project is intentionally opinionated about verification: a system should not claim success simply because a model said it worked or because CI passed.

## Architecture questions

### Why use an API gateway?

Clients get one stable ingress rather than depending directly on every internal service. The gateway is the natural place for routing and cross-cutting enforcement such as CORS, authorization assumptions, rate-related controls and request correlation. The trade-off is that it becomes critical infrastructure, so it needs health, resilience and observability.

### Why keep services separate instead of one large application?

The separation is used only where there is a distinct responsibility: identity/token lifecycle, user-domain logic, audit/event consumption and orchestration have different trust, persistence and scaling concerns. The goal is not “microservices for the sake of microservices”; it is explicit boundaries that can be reasoned about and tested.

### Why PostgreSQL?

Identity/user state benefits from transactions, constraints and a mature relational model. It is predictable, widely used in production systems and makes consistency rules visible rather than hiding them behind application code.

### Why Redis?

Redis is useful for distributed low-latency state such as caches and rate/policy support. The important design rule is that Redis failure must not silently weaken authorization or owner policy.

### Why RabbitMQ?

Some work, especially audit/event processing, does not need to block the synchronous request path. A message broker demonstrates asynchronous communication while making delivery and trust boundaries explicit.

### Why DTOs instead of returning persistence entities?

Persistence models and public API contracts evolve for different reasons. DTOs reduce accidental field exposure, provide a clean validation boundary and prevent the public contract from being tightly coupled to JPA/database structure.

## Security and identity questions

### How does authentication work?

The identity service exposes `/api/auth` registration, login, refresh and logout flows. JWT/refresh-token foundations are used for authenticated API access. Refresh tokens are credentials and should be rotated/revoked/validated rather than treated like harmless identifiers.

### Why not trust the gateway completely?

A gateway is a useful enforcement point, but downstream services should still preserve their authorization assumptions. A compromised/misconfigured route should not automatically turn every service into an unguarded trust zone.

### What is your secret-management position?

Development examples are intentionally local defaults only. Real deployments require dedicated secret management, TLS, hardened identity configuration and production-specific deployment values. Secrets must never be committed, logged or included in evidence artifacts.

## AI and governance questions

### What is the difference between Syntra and Aetheris?

Syntra is the user-facing assistant/persona and coordination experience. Aetheris is the infrastructure/control plane that provides services, agents, tools, policy, approvals, evidence, automation and observability. Keeping those responsibilities separate makes the assistant flexible without making the model the authority.

### Why are models not the policy authority?

LLMs are probabilistic and can make inconsistent decisions. Policy, emergency controls and approval requirements must be deterministic and inspectable. The model can propose a plan, but the platform decides whether that plan is eligible to proceed.

### What does `ALLOW` mean?

Only that preflight policy allows the action to proceed. It does not mean execution occurred. The platform should observe execution and verify the result before claiming completion.

### How do emergency controls work conceptually?

The deterministic precedence is:

```text
STOP > TAKE_CONTROL > PAUSE > NORMAL
```

A lower-priority automation state cannot override a higher-priority emergency instruction.

### How do Private and Zero-Cost modes fit the design?

They are owner-policy constraints. Zero-Cost can hard-block configured billable fallback paths. Private mode constrains protected data to approved local paths unless an explicit owner/policy exception is present. The goal is to make those guarantees testable rather than just prompts given to a model.

## Reliability and observability questions

### What happens if a service becomes unavailable?

Failure should be visible, bounded and not silently converted into unsafe behavior. Resilience policies can apply timeouts/circuit-breaking/retries only where appropriate, while metrics/logs/traces provide evidence for diagnosis.

### Why use Prometheus, Grafana, Loki, Tempo and OpenTelemetry?

They cover complementary observability dimensions: metrics, visualization, logs and distributed traces. Using standard tooling also demonstrates how a distributed request can be followed across services rather than debugging only from application console output.

### Is observability proof that a user action succeeded?

No. Telemetry is evidence about system behavior. Business-level success still requires an explicit verification step.

## DevOps and release questions

### Why support both Docker Compose and Kubernetes/Helm?

Compose gives a fast local integration path. Kubernetes/Helm demonstrate orchestration, health and deployment concepts. Keeping both makes development practical while still showing cloud-native design.

### What does reproducible build evidence add?

The repository runs deterministic dependency/lock checks and a two-pass build comparison. That reduces the gap between “CI was green once” and “the same inputs produce the expected build outputs.” It is still repository evidence, not physical-machine validation.

### Why is `main` protected?

Stable history is now protected by the `Protect stable main` ruleset. Changes are expected to pass through the canonical development/promotion path and configured status checks instead of being pushed directly into stable history.

## Trading questions

### Does Aetheris autonomously trade real money?

No. The repository contains a fail-closed trading-intelligence/risk foundation, but live-money execution is outside the default trusted path. Withdrawals/transfers are not AI authority. Any future execution path would require separate evidence, controls and explicit owner authorization.

## Physical-PC questions

### Is the entire local AI system already validated on the target computer?

No. The correct status is `BLOCKED_PENDING_HARDWARE`. Hosted CI validates repository behavior; it cannot prove WSL2, Docker Desktop, NVIDIA GPU acceleration, microphone/voice behavior, local-model latency, thermals, storage health or browser/phone integrations on a machine that has not been tested.

### Why call that out so prominently?

Because engineering credibility depends on distinguishing what is implemented from what is proven. I would rather show an explicit pending validation boundary than claim hardware behavior I have not measured.

## Strong demo sequence for an interview

1. Show the README architecture and explain Syntra vs Aetheris.
2. Start the core stack with `docker compose up --build`.
3. Open the dashboard and gateway surfaces.
4. Demonstrate identity/user/audit behavior and explain the request path.
5. Enable the observability profile and show how service behavior is inspected.
6. Show the governance lifecycle and Stage 33/34 documentation.
7. Show the protected `main` ruleset and CI/reproducibility gates.
8. End with the physical-PC truth boundary and the next evidence-driven execution phase.

See [`demo-guide.md`](demo-guide.md) for the reproducible demo checklist.

## Things not to claim

Do not say any of the following unless new evidence is added later:

- “The full system is production-ready.”
- “The physical target PC is validated.”
- “The AI has unrestricted admin control of the computer.”
- “Aetheris autonomously trades live money.”
- “Hosted CI proves GPU, voice or thermal performance.”
- “An `ALLOW` policy decision proves execution succeeded.”

## Closing answer: what did you learn?

A strong answer is that the project changed from “build more features” into “build explicit boundaries and evidence.” The hardest parts are not adding another service or AI prompt; they are deciding who is trusted, what happens during failure, how an action is authorized, how success is verified and how those decisions stay understandable to another engineer.
