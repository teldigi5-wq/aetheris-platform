# Portfolio Scope and Maturity

This document separates the parts of Aetheris that should be presented as the primary portfolio project from later AI-runtime/control-plane work.

The goal is simple: make it easy for a reviewer to distinguish **implemented and defensible platform engineering** from **runtime research or pre-PC capabilities that still require environment validation**.

For a proof-oriented review, use these documents alongside this scope guide:

- **[Core Platform Evidence Pack](portfolio-evidence.md)** — machine-traceable platform claims, source/config paths and reproducible verification commands.
- **[Core Platform Portfolio Case Study](portfolio-case-study.md)** — concise problem, architecture, design trade-offs and interview narrative.
- **[`teldigi5-wq/aetheris-ai-runtime`](https://github.com/teldigi5-wq/aetheris-ai-runtime)** — the separate repository that owns orchestration, workstation, reasoning and quantitative runtime source.

The platform evidence pack is backed by `build-evidence/portfolio/core-platform-evidence.json`, `tools/validate_portfolio_evidence.py` and the dedicated `Portfolio Evidence` CI workflow. Repository proof is intentionally kept separate from physical-machine proof.

## 1. Primary portfolio track — cloud-native platform engineering

This is the recommended recruiter/interview narrative.

### Core services

- Spring Cloud Gateway as the ingress layer;
- Identity service for registration, login, refresh and logout;
- User service for user-domain behavior and persistence;
- Audit service for asynchronous event/audit handling;
- PostgreSQL for durable relational state;
- Redis for caching/rate-related distributed concerns;
- RabbitMQ for asynchronous messaging.

### Security and identity

The project treats refresh tokens as credentials rather than opaque identifiers. Rotation/revocation, token hashing and explicit lifecycle handling are part of the design.

The project also separates persistence entities from public API contracts and keeps secret-management assumptions explicit.

### Reliability

Resilience4j is used for bounded resilience behavior. Retry is not treated as universally safe: mutating work must not be blindly repeated simply because a request failed.

### Observability

The platform repository includes OpenTelemetry, Prometheus, Grafana, Loki and Tempo. The portfolio value is not the number of tools; it is the ability to explain what each signal is for, how a request is followed across services and how failures are diagnosed.

### Deployment

The platform repository includes Docker / Docker Compose integration, Kubernetes assets, Helm packaging/configuration, health/readiness concepts, replica/scaling and recovery-oriented deployment work.

This track is the strongest evidence for backend, platform, DevOps/SRE and distributed-systems interviews.

## 2. Secondary track — external Syntra / Aetheris AI runtime research

The AI/control-plane work was added after the core platform foundation. Its runtime source is now intentionally owned by the separate `teldigi5-wq/aetheris-ai-runtime` repository rather than duplicated inside `aetheris-platform`.

### Runtime-owned implementations

`aetheris-ai-runtime` owns source and runtime-focused tests/certification for:

- `orchestrator-service` — orchestration, agent catalog and governance foundations;
- `workstation-agent` — workstation/browser/PC automation foundations;
- `aetheris-reasoning` — reasoning and verification components;
- `aetheris-quant` — fail-closed quantitative/trading-intelligence foundations.

This platform repository retains the cross-repository boundary, integration path, certification reference, historical evidence and platform-side validation needed to consume the runtime safely. Key assets include `contracts/ai-runtime-boundary.v1.json`, `architecture/ai-runtime-certification-reference.json` and `docker-compose.integration-external.yml`.

The currently certified runtime checkpoint recorded by the platform is `6c714d1772db2db490cd035e11a78308f26f8a63`. That is a certification reference, not a claim that future runtime development is frozen at that SHA.

These are valid engineering artifacts, but they are not all at the same runtime maturity.

## 3. Maturity labels

Use the following meanings consistently.

| Label | Meaning |
|---|---|
| **CORE_IMPLEMENTED** | Concrete platform functionality in the main backend/deployment story and suitable for primary portfolio discussion. |
| **REPOSITORY_TESTED** | Code exists in its owning repository and repository tests/CI cover the behavior, but it may depend on future environment integration. |
| **TEMPLATE_ONLY** | A deterministic workflow/template exists, but exact external-site behavior has not been physically validated. |
| **BLOCKED_PENDING_HARDWARE** | The feature requires the owner target PC or local runtime evidence before it can be claimed operational. |
| **DISABLED_BY_POLICY** | The project intentionally prevents the capability from operating by default, e.g. live-money execution. |

## 4. Current maturity map

| Capability | Source owner | Maturity | Portfolio guidance |
|---|---|---|---|
| Gateway / backend services | Platform | CORE_IMPLEMENTED | Lead with this. |
| Identity / JWT / refresh lifecycle | Platform | CORE_IMPLEMENTED | Strong security discussion area. |
| PostgreSQL / Redis / RabbitMQ | Platform | CORE_IMPLEMENTED | Strong distributed-systems discussion area. |
| Observability stack | Platform | CORE_IMPLEMENTED | Strong SRE/platform discussion area. |
| Resilience4j policies | Platform | CORE_IMPLEMENTED | Explain safe-vs-unsafe retry trade-offs. |
| Docker / Kubernetes / Helm | Platform | CORE_IMPLEMENTED | Strong DevOps/cloud-native discussion area. |
| Orchestrator / governance | AI runtime | REPOSITORY_TESTED | Secondary discussion; use runtime evidence. |
| Reasoning / digital-twin foundations | AI runtime / retained contracts | REPOSITORY_TESTED | Describe as research/control-plane work, not autonomous-intelligence proof. |
| Generic browser operator | AI runtime | BLOCKED_PENDING_HARDWARE | Do not claim real owner-PC browser operation yet. |
| LinkedIn site skills | AI runtime research assets | TEMPLATE_ONLY | Exact selectors/session flow still need physical validation. |
| Vercel site skills | AI runtime research assets | TEMPLATE_ONLY | Exact selectors/session flow still need physical validation. |
| PC-care integration | AI runtime | BLOCKED_PENDING_HARDWARE | Physical workstation evidence is still required. |
| Live-money trading | AI runtime | DISABLED_BY_POLICY | Never present as active autonomous live-money execution. |

## 5. How to present the project in an interview

A strong sequence is:

1. explain the gateway and service boundaries;
2. explain authentication/token lifecycle;
3. show PostgreSQL, Redis and RabbitMQ responsibilities;
4. show observability and resilience decisions;
5. show Docker/Kubernetes/Helm deployment work;
6. explain the explicit platform/runtime repository boundary;
7. only then discuss optional orchestration/operator research if relevant to the role.

Avoid opening with the 34-stage research roadmap. The roadmap is historical engineering context, not the strongest first impression.

## 6. Roadmap-history note

The later Syntra × Aetheris roadmap reached **Stage 34 / 34** at the repository-contract level. That statement remains in historical documentation because validators and references depend on it.

It does **not** mean:

- every planned subsystem is production-ready;
- the owner target PC has been validated;
- every browser/social integration works;
- the AI has unrestricted host authority;
- a runtime artifact has been published to a production registry;
- live-money execution is enabled.

**Physical-machine status remains `BLOCKED_PENDING_HARDWARE`.** Hosted CI is repository evidence, not a substitute for physical owner-PC validation.

## 7. Why the history is not being rewritten

The repositories have visible development and extraction history. Rewriting or force-cleaning published history only to make the project look simpler can reduce trust and break references.

The preferred approach is therefore:

- preserve truthful history;
- make current source ownership and maturity obvious;
- keep the recruiter narrative centered on the defensible core platform;
- keep runtime source in its owning repository instead of restoring duplicate copies;
- advance cross-repository certification references only after evidence is green.

That keeps the project auditable without letting experimental work dominate the first impression.