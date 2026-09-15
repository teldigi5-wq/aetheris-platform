# Portfolio Scope and Maturity

This document separates the parts of Aetheris that should be presented as the primary portfolio project from later experimental/control-plane work.

The goal is simple: make it easy for a reviewer to distinguish **implemented and defensible platform engineering** from **repository-side research or pre-PC capabilities that still require runtime validation**.

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

The repository includes standard observability components:

- OpenTelemetry;
- Prometheus;
- Grafana;
- Loki;
- Tempo.

The portfolio value is not the number of tools. It is the ability to explain what each signal is for, how a request is followed across services and how failures are diagnosed.

### Deployment

The repository includes:

- Docker / Docker Compose integration;
- Kubernetes assets;
- Helm packaging/configuration;
- health/readiness concepts;
- replica/scaling and recovery-oriented deployment work.

This track is the strongest evidence for backend, platform, DevOps/SRE and distributed-systems interviews.

## 2. Secondary track — Syntra / Aetheris control-plane research

This track was added after the core platform foundation. It is useful, but it should be described with more precise maturity labels.

### Repository-implemented extensions

The repository includes implementation and tests for areas such as:

- orchestration and agent catalog foundations;
- deterministic owner-policy evaluation;
- scoped approvals;
- action auditing and verification semantics;
- reasoning/verification components;
- digital-twin and bounded recovery foundations;
- automation/emergency-control foundations;
- generic browser/operator control-plane code;
- site-skill templates for LinkedIn and Vercel;
- PC-care planning foundations;
- fail-closed trading-intelligence/risk foundations.

These are valid engineering artifacts, but they are not all at the same runtime maturity.

## 3. Maturity labels

Use the following meanings consistently.

| Label | Meaning |
|---|---|
| **CORE_IMPLEMENTED** | Concrete platform functionality in the main backend/deployment story and suitable for primary portfolio discussion. |
| **REPOSITORY_TESTED** | Code exists and repository tests/CI cover the behavior, but it may depend on future environment integration. |
| **TEMPLATE_ONLY** | A deterministic workflow/template exists, but exact external-site behavior has not been physically validated. |
| **BLOCKED_PENDING_HARDWARE** | The feature requires the owner target PC or local runtime evidence before it can be claimed operational. |
| **DISABLED_BY_POLICY** | The repository intentionally prevents the capability from operating by default, e.g. live-money execution. |

## 4. Current maturity map

| Capability | Maturity | Portfolio guidance |
|---|---|---|
| Gateway / backend services | CORE_IMPLEMENTED | Lead with this. |
| Identity / JWT / refresh lifecycle | CORE_IMPLEMENTED | Strong security discussion area. |
| PostgreSQL / Redis / RabbitMQ | CORE_IMPLEMENTED | Strong distributed-systems discussion area. |
| Observability stack | CORE_IMPLEMENTED | Strong SRE/platform discussion area. |
| Resilience4j policies | CORE_IMPLEMENTED | Explain safe-vs-unsafe retry trade-offs. |
| Docker / Kubernetes / Helm | CORE_IMPLEMENTED | Strong DevOps/cloud-native discussion area. |
| Orchestrator/governance service | REPOSITORY_TESTED | Secondary discussion. |
| Reasoning / digital-twin foundations | REPOSITORY_TESTED | Describe as research/control-plane work, not autonomous intelligence proof. |
| Generic browser operator | BLOCKED_PENDING_HARDWARE | Do not claim real browser operation yet. |
| LinkedIn site skills | TEMPLATE_ONLY | Exact selectors/session flow still need physical validation. |
| Vercel site skills | TEMPLATE_ONLY | Exact selectors/session flow still need physical validation. |
| PC-care integration | BLOCKED_PENDING_HARDWARE | Physical workstation evidence is still required. |
| Live-money trading | DISABLED_BY_POLICY | Never present as an active autonomous trading capability. |

## 5. How to present the repository in an interview

A strong sequence is:

1. explain the gateway and service boundaries;
2. explain authentication/token lifecycle;
3. show PostgreSQL, Redis and RabbitMQ responsibilities;
4. show observability and resilience decisions;
5. show Docker/Kubernetes/Helm deployment work;
6. only then discuss the optional orchestration/operator track if relevant to the role.

Avoid opening with the 34-stage research roadmap. The roadmap is historical engineering context, not the strongest first impression.

## 6. Roadmap-history note

The later Syntra × Aetheris roadmap reached **Stage 34 / 34** at the repository-contract level. That statement remains in the repository because validators and historical documentation depend on it.

It does **not** mean:

- every planned subsystem is production-ready;
- the owner target PC has been validated;
- every browser/social integration works;
- the AI has unrestricted host authority;
- live-money execution is enabled.

**Physical-machine status remains `BLOCKED_PENDING_HARDWARE`.**

## 7. Why the history is not being rewritten

The repository has visible development history, including the later control-plane expansion. Rewriting or force-cleaning published history only to make the project look simpler can reduce trust and may break references.

The preferred approach is therefore:

- preserve truthful history;
- make the current architecture and maturity obvious;
- keep the recruiter narrative centered on the defensible core platform;
- remove stale temporary branches only during final repository cleanup when their commits are already safely preserved elsewhere.

That keeps the project auditable without letting experimental work dominate the first impression.
