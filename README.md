<div align="center">

# Aetheris Platform

### Cloud-native distributed systems engineering with an external AI runtime integration

**A portfolio project centered on secure backend services, distributed systems, observability, resilience and Kubernetes — with Syntra/Aetheris automation research separated into an independently certified runtime repository.**

![Main](https://img.shields.io/badge/main-protected-2563eb?style=for-the-badge&logo=github)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-TypeScript-61DAFB?style=for-the-badge&logo=react&logoColor=111827)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)

`Java 21` • `Spring Boot` • `PostgreSQL` • `Redis` • `RabbitMQ` • `Resilience4j` • `OpenTelemetry` • `Prometheus` • `Grafana` • `Loki` • `Tempo` • `Kubernetes` • `Helm` • `React + TypeScript`

</div>

---

## Portfolio pitch

The strongest way to evaluate Aetheris is as a **platform-engineering project first**.

The core system demonstrates a coherent progression through:

1. API gateway and service boundaries;
2. identity, JWT access tokens and refresh-token lifecycle;
3. PostgreSQL persistence, Redis caching and traffic controls;
4. asynchronous RabbitMQ events and audit processing;
5. metrics, logs and traces with OpenTelemetry and the Grafana stack;
6. bounded resilience with safe retry behavior;
7. Docker, Kubernetes and Helm deployment foundations.

Those areas are the **primary recruiter/interview story** because they are concrete, independently understandable and directly visible in code and deployment assets.

The later Syntra/Aetheris orchestration, reasoning, workstation and quantitative runtime source has been extracted to the separate [`teldigi5-wq/aetheris-ai-runtime`](https://github.com/teldigi5-wq/aetheris-ai-runtime) repository. This repository retains the platform-side contracts, integration path, certification references and evidence needed to consume that runtime without duplicating its source.

See **[Portfolio scope & maturity](docs/portfolio-scope.md)** for the exact separation.

---

## Platform / AI runtime boundary

Aetheris now has an explicit two-repository ownership model.

| Repository | Owns |
|---|---|
| `aetheris-platform` | Gateway, identity, user and audit services; dashboard; observability; deployment assets; platform contracts; cross-repository integration and evidence |
| `aetheris-ai-runtime` | `orchestrator-service`, `workstation-agent`, `aetheris-reasoning`, `aetheris-quant` and their runtime-owned certification assets |

The currently certified runtime checkpoint recorded by the platform is:

`teldigi5-wq/aetheris-ai-runtime@65a6262717adcd52ac8d8a16ed6f223e299fd74d`

That SHA is a **certification reference**, not a claim that the runtime repository can never advance. Platform integration should move to a newer runtime only through the same evidence-driven certification process.

Boundary assets include:

- `contracts/ai-runtime-boundary.v1.json` — cross-repository contract;
- `architecture/ai-runtime-certification-reference.json` — certified runtime reference and truth boundaries;
- `architecture/ai-runtime-extraction-manifest.json` — extraction ownership record;
- `docker-compose.integration-external.yml` — external-runtime integration path;
- `tools/load_certified_ai_runtime.sh` — certified-runtime loading helper.

The extracted runtime-owned source roots must not be reintroduced into this repository as duplicate implementations.

---

## What to review first

| If you have... | Start here |
|---|---|
| **2 minutes** | This README → **Core platform track** → architecture diagram |
| **10 minutes** | [Demo guide](docs/demo-guide.md) → gateway/auth → messaging → observability |
| **Interview prep** | [Interview guide](docs/interview-guide.md) → [Architecture decisions](docs/architecture-decisions.md) |
| **AI/operator interest** | [Portfolio scope & maturity](docs/portfolio-scope.md) → [Architecture](docs/architecture.md) |
| **Repository governance interest** | [Verification checklist](docs/verification-checklist.md) → [Master roadmap](docs/master-roadmap.md) |

---

## Core platform track — primary portfolio story

### 1. Gateway and service boundaries

A Spring Cloud Gateway provides one ingress surface in front of independently bounded services. The platform repository separates identity, user-domain and audit/event responsibilities. Requests for AI orchestration cross a defined repository boundary into the external Aetheris AI runtime.

### 2. Identity and token lifecycle

The identity service implements registration, login, refresh and logout flows around JWT access tokens and refresh-token state. Refresh tokens are treated as credentials: rotation/revocation and stored token hashes are part of the design rather than storing reusable plaintext refresh credentials.

### 3. Persistence, cache and traffic controls

PostgreSQL provides durable relational state. Redis supports low-latency distributed concerns such as caching/rate-related controls. Security-sensitive behavior is designed to fail closed rather than silently weakening authorization when an infrastructure dependency is unhealthy.

### 4. Messaging and audit flow

RabbitMQ supports asynchronous event delivery so audit/event processing does not have to block every synchronous user request. This creates an explicit delivery and failure boundary that can be observed and reasoned about.

### 5. Observability

The project includes OpenTelemetry instrumentation foundations plus Prometheus, Grafana, Loki and Tempo for metrics, visualization, logs and traces.

### 6. Resilience

Resilience4j policies demonstrate circuit breaking and bounded retry behavior. Retrying is intentionally constrained to operations where repetition is safe rather than being applied blindly to mutating requests.

### 7. Deployment

Docker Compose provides the fast local integration path. Kubernetes and Helm assets demonstrate health/readiness, replicas, deployment topology and cloud-native operational concepts.

---

## Core architecture

```mermaid
flowchart LR
    CLIENT[Dashboard / Client] --> GW

    subgraph PLATFORM[aetheris-platform]
        GW[API Gateway]
        ID[Identity Service]
        USER[User Service]
        AUDIT[Audit Service]
        PG[(PostgreSQL)]
        REDIS[(Redis)]
        MQ[(RabbitMQ)]
        OTEL[OpenTelemetry]
        OBS[Prometheus / Grafana / Loki / Tempo]

        GW --> ID
        GW --> USER
        GW --> AUDIT
        ID --> PG
        USER --> PG
        GW --> REDIS
        USER --> MQ
        MQ --> AUDIT
        GW --> OTEL
        ID --> OTEL
        USER --> OTEL
        AUDIT --> OTEL
        OTEL --> OBS
    end

    subgraph RUNTIME[aetheris-ai-runtime — external certified runtime]
        ORCH[Orchestrator Service]
        REASON[Aetheris Reasoning]
        WORK[Workstation Agent]
        QUANT[Aetheris Quant]
    end

    GW -->|boundary contract| ORCH
    ORCH --> REASON
    ORCH --> WORK
    ORCH --> QUANT
```

The core platform story remains useful even if the optional AI/operator track is ignored entirely.

---

## External AI/control-plane extensions

The later AI/control-plane work is intentionally presented as a **secondary engineering/research track**. Runtime implementation lives in `aetheris-ai-runtime`; this platform repository owns the integration boundary and evidence that connects it to the core system.

| Extension | Source owner | Current maturity |
|---|---|---|
| Agent/orchestration service | AI runtime | Runtime implemented and repository-tested |
| Deterministic owner policy / scoped approvals | AI runtime + platform boundary evidence | Repository implemented and regression-tested |
| Reasoning / verification components | AI runtime | Repository implemented; not a claim of human-level autonomy |
| Digital-twin / recovery foundations | AI runtime / retained platform contracts | Bounded research foundations |
| Generic browser operator | AI runtime | Repository adapter exists; physical runtime not yet validated |
| LinkedIn/Vercel site skills | AI runtime research assets | Templates only; exact site flows not physically validated |
| PC-care / workstation integration | AI runtime | `BLOCKED_PENDING_HARDWARE` |
| Trading intelligence | AI runtime | Fail-closed research/risk foundation; live-money authority disabled |

The architectural rule is simple: **models may propose actions, but they are not the final policy authority**. Automation remains subject to deterministic policy, approval and verification rules.

---

## Quick start

### Prerequisites

- Git
- Docker with Docker Compose v2

Java 21, Node and Python are needed only when running individual modules or repository validators directly.

### Start the core platform stack

```bash
git clone https://github.com/teldigi5-wq/aetheris-platform.git
cd aetheris-platform
docker compose up --build
```

Core local surfaces:

| Surface | Local address |
|---|---|
| Dashboard | `http://localhost:3000` |
| API Gateway | `http://localhost:8080` |
| User Service | `http://localhost:8081` |
| Identity Service | `http://localhost:8082` |
| Audit Service | `http://localhost:8083` |
| RabbitMQ management | `http://localhost:15672` |

The Orchestrator surface on `http://localhost:8090` belongs to the **external `aetheris-ai-runtime` integration**, not to a runtime source tree in this repository. Use the certified external-runtime integration assets when that surface is required.

Compose credentials and fallback secrets are **development-only defaults**.

### Start observability

```bash
docker compose --profile observability up --build
```

Additional surfaces include Grafana on `http://localhost:3001`, Prometheus on `http://localhost:9090`, Tempo on `http://localhost:3200` and Loki on `http://localhost:3100`.

See **[docs/demo-guide.md](docs/demo-guide.md)** for the recommended reviewer sequence.

---

## Security and reliability highlights

- access/refresh token lifecycle is treated as a credential-management problem, not just JWT generation;
- persistence entities are not used as public API contracts by default;
- Redis/RabbitMQ failures are explicit infrastructure failures, not reasons to weaken authorization;
- retries are limited to operations where repetition is safe;
- secrets should never be committed, logged or included in evidence artifacts;
- stable `main` is protected and changes are promoted through pull requests and CI;
- dependency lockdown, CodeQL and reproducibility checks provide repository-level evidence.

For the detailed security model, see **[Token flow and threat model](docs/security/token-flow.md)**.

---

## Kubernetes / Helm and operational story

The deployment track is intended to demonstrate more than “I wrote a Dockerfile.” Reviewers can inspect Kubernetes/Helm deployment assets, health/readiness behavior, replica configuration and scaling concepts, observability integration, and recovery behavior when services become unavailable.

See **[Architecture](docs/architecture.md)** and **[Demo guide](docs/demo-guide.md)**.

---

## AI/operator track: explicit truth boundary

The Syntra/Aetheris runtime has repository evidence, but some claims depend on hardware and authenticated external services that have not yet been physically tested on the target PC.

**Physical-machine status:** `BLOCKED_PENDING_HARDWARE`  
**Physical-PC validation remains pending.** Hosted CI and repository evidence are not substitutes for validation on the owner's target machine.

The project therefore does **not** claim that WSL2, Docker Desktop, GPU acceleration, local-model latency, thermals, voice hardware, Chrome/Edge WebDriver sessions, LinkedIn automation or Vercel browser automation are validated on the future owner PC.

It also makes **no production-activation, registry-publication or live-money execution claim** from repository/hosted-CI evidence alone.

---

## Current engineering status (25 October 2026)

**Release state: DRAFT / NOT RELEASE-CERTIFIED. Physical PC: `BLOCKED_PENDING_HARDWARE`.** The original Stage 34 / 34 master roadmap is a completed repository blueprint, not the assertion that the production agent system works on a real Windows workstation. There is no Stage 35.

- **Platform security:** [PR #262](https://github.com/teldigi5-wq/aetheris-platform/pull/262) passed 29/29 workflow runs. Newer reconciled [PR #264](https://github.com/teldigi5-wq/aetheris-platform/pull/264) initially passed 29/30; its data-recovery build failed on Maven Central HTTP 502 downloading `objenesis` before recovery assertions. **The failed job was rerun unchanged and succeeded; the same candidate SHA now reports 30/30 successful workflow runs.** This is CI evidence, not physical-machine or production certification.
- **Current User/Audit authorization research:** [PR #267](https://github.com/teldigi5-wq/aetheris-platform/pull/267) adds direct signed JWT filters; [#268](https://github.com/teldigi5-wq/aetheris-platform/pull/268) hardens integral identities; [#269](https://github.com/teldigi5-wq/aetheris-platform/pull/269) verifies real User/Audit loopback HTTP; [#270](https://github.com/teldigi5-wq/aetheris-platform/pull/270) aligns Gateway ADMIN/scopes; [#271](https://github.com/teldigi5-wq/aetheris-platform/pull/271) **2/2 Windows/Ubuntu CI jobs passed** consuming actual Identity-issued synthetic tokens in Gateway and real User/Audit HTTP. All are stacked draft/unmerged. No production Identity login → HTTP Gateway proxy, container/Pod network, object-ownership or key-rotation certification yet. [Exact contract scope](docs/security/identity-jwt-cross-service-issuer-contract-20261025.md).
- **New Gateway HTTP proxy research:** [Draft PR #272](https://github.com/teldigi5-wq/aetheris-platform/pull/272) has **2/2 Ubuntu/Windows CI jobs passed** ([run #40178864044](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40178864044)). Real embedded Gateway Netty HTTP now forwards actual Identity-issued test JWTs to a **disposable local responder**; 3/3 proxy cases per OS verified trusted ADMIN identity headers, denied nonadmins/expired/tampered requests before outbound forwarding and rejected Audit POST with only `services:read`. Audit write methods now require `services:write` at Gateway, matching downstream policy; Identity grants no new scope. Production Redis/circuit-breaker routes and actual User/Audit containers were **not** in this proxy experiment. [Scope/evidence](docs/security/gateway-real-http-proxy-audit-scope-20261025.md). No protected-main merge or release certification.
- **Actual disposable backend container authorization:** [draft PR #273](https://github.com/teldigi5-wq/aetheris-platform/pull/273) passed its [initial Ubuntu Docker job](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40180192714): the **real Gateway, User and Audit containers** operated with isolated disposable Postgres/Redis/RabbitMQ on an internal-only no-host-ports network. A seventh isolated caller container used Identity-issued synthetic JWTs to verify **26/26 direct-container and Gateway HTTP authorization outcomes** (ADMIN 200, anonymous/expired/tampered 401, DEVELOPER/API_CONSUMER 403; Audit write with read-only ADMIN 403). All containers/networks/data removed after CI. [Test scope](docs/security/direct-backend-container-probe-20261025.md). This covers application JWT enforcement over a real Docker network, **not** production Kubernetes network policies, resource ownership, live Identity login or release GO.
- **Verified synthetic resource-ID trust-boundary regression:** [B4Q draft PR](https://github.com/teldigi5-wq/aetheris-platform/pull/274) uses the real Identity JWT issuer to create non-admin tokens with `uid=1`, then tests against a separately stored synthetic User record also numbered `1` using two real HTTP routes (Gateway and direct backend). Matching numeric IDs must **not** confer ownership. Twenty non-admin negative cases plus authorized ADMIN create/read/delete and post-denial integrity checks yield **30/30 verified real-container HTTP assertions**, plus parent **26/26** ([initial Actions #40181884731](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40181884731)), on ephemeral Postgres only. The existing ADMIN-only policy and production components are untouched. [Review scope](docs/security/signed-uid-not-resource-owner-20261025.md). This is not a completed tenant/ownership model.
- **New PostgreSQL backup/restore validation rehearsal:** [Draft PR #275](https://github.com/teldigi5-wq/aetheris-platform/pull/275) passed its [implementation CI #40185049169](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40185049169). Real Postgres 16 `pg_dump -Fc` and `pg_restore` replicated one synthetic User record into a new disposable database; the same real User image booted with `ddl-auto=validate`, passed **7/7** signed JWT/row-read HTTP checks, and left normalized schema/data dumps **identical**. A second clone missing `users.email` correctly refused startup with a Hibernate missing-column reason. The run also revalidated **26/26 + 30/30** earlier real-container access checks. [Rehearsal limits](docs/database/DISPOSABLE_USER_RESTORE_VALIDATE_20261025.md). **Does not** approve changing persistent Compose `ddl-auto:update`, production migrations, backup restore or issue #242 closure.
- **Cross-branch modern migration-readiness evidence:** [draft PR #276](https://github.com/teldigi5-wq/aetheris-platform/pull/276) verified the **exact SHA-pinned synthetic V1/V2 SQL from older Flyway lab #247** against actual current Identity/User images, in a [passing isolated Docker run](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40186604237). Modern Hibernate-created User catalog column/PK/unique semantics matched; both applications booted with strict `ddl-auto=validate`; **7/7** signed-identity HTTP probes passed; complete normalized schema/data dumps were unchanged; a missing Identity email column caused deliberate validation refusal. [Evidence and limitations](docs/database/MODERN_PR247_SCHEMA_PARITY_20261025.md). **No real operator schema, production Flyway execution, historic baseline, migration cutover or main merge.** Issue #242 stays open.
- **Actual Audit Flyway migration ownership experiment:** [draft PR #277](https://github.com/teldigi5-wq/aetheris-platform/pull/277) passed [initial Docker CI #40187951448](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40187951448). The **real Audit Docker image** ran its bundled V1 migration against an empty isolated PostgreSQL DB with `baseline-on-migrate=false`, recorded exactly one SQL success with no automatic baseline, and a second image startup reused its history. **12/12 signed JWT HTTP checks passed** across both instances. The same image **refused to auto-baseline** a separate nonempty Identity/User synthetic database and left it without Audit tables/history. The production Audit config **still has `baseline-on-migrate: true`**, and PR #277 does not change that setting, operator schemas, production Compose or protected `main`. [Boundaries](docs/database/AUDIT_FLYWAY_OWNERSHIP_NO_AUTO_BASELINE_20261025.md). Issue #242 remains open.
- **Audit Flyway shipped default-deny remediation — draft:** [PR #278](https://github.com/teldigi5-wq/aetheris-platform/pull/278) changes the actual Audit `application.yml` default to **`baseline-on-migrate: false`** and removes the test environment overrides. [Initial working Ubuntu Docker CI #40189654039](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40189654039) passed five source/configuration guards plus real Audit V1 migration, history reuse, **12/12** signed-token HTTP checks and rejection of silent baselining in a nonempty shared synthetic database. Existing Audit installations without approved Flyway history **may refuse startup after promotion**. This change is ONLY in an unmerged draft, NOT deployed, and requires operator inventory/backup/rollback approval before merging. [Compatibility and risk review](docs/database/AUDIT_DEFAULT_DENY_BASELINE_REVIEW_20261025.md). Issue #242 remains open.
- **Remaining security release blockers:** persistent DB migration [#242](https://github.com/teldigi5-wq/aetheris-platform/issues/242), direct backend authorization [#251](https://github.com/teldigi5-wq/aetheris-platform/issues/251), Java runtime image patching [#255](https://github.com/teldigi5-wq/aetheris-platform/issues/255), final advisory/SBOM evidence [#259](https://github.com/teldigi5-wq/aetheris-platform/issues/259), credential/backup/network/rollback verification.
- **Runtime and Syntra:** latest draft reference skills [runtime PR #186](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/186) passed 3/3 CI jobs; skill advice remains default-off and does not certify a website-generating autonomous agent. Runtime security [PR #185](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/185) is independently unmerged.
- **Next work:** validate a single security release candidate; reconcile strict runtime security ancestry; complete independent E3.9 model/browser evaluations; progress the proposed E4–E9 capability roadmap and owner-PC [Execution Phase A](https://github.com/teldigi5-wq/aetheris-platform/issues/49). No live-money or unrestricted host action authority.

See the [current release and development gates](docs/current-release-roadmap-20261025.md) for the detailed issue/PR ledger and safe continuation order. This status section reports evidence, not an automated release decision.

---
## Roadmap history and why Stage 34 / 34 still appears

The repository contains two historical tracks:

1. the original cloud-native platform progression, which is the primary portfolio narrative;
2. the later Syntra × Aetheris master roadmap, which expanded the project into AI orchestration, governance and pre-PC automation research before runtime ownership was extracted into its own repository.

The later repository roadmap reached **Stage 34 / 34**. That statement is retained for historical/validation-contract consistency; it is **not** the headline claim for the portfolio and it does not mean every aspirational subsystem is production-ready.

There is no Stage 35. New work after that roadmap is treated as normal engineering passes or versioned capabilities, not as an attempt to inflate the stage count.

For the full historical record see **[docs/master-roadmap.md](docs/master-roadmap.md)**.

---

## Verification and stable history

Stable `main` is protected by the repository ruleset **`Protect stable main`**.

The Stage 27 governance contract still records `feature/syntra-aetheris-foundation-v2` as the canonical development-line marker used by its historical/repository-governance verification. The completed runtime extraction and stable-main promotion do not rewrite that certification contract.

The verification surface includes backend tests, dashboard builds, compatibility-contract freeze, CodeQL for Java and JavaScript/TypeScript, dependency lockdown, reproducibility checks, platform evidence certification and cross-repository runtime-integration proofs.

Runtime-owned regression/safety certification executes in `aetheris-ai-runtime`; the platform retains the certified reference and the integration/evidence checks needed to consume it. Historical Stage 26 safety/evidence and Stage 27 repository-governance contracts remain CI guards, not the primary recruiter narrative.

Hosted CI is repository evidence, **not physical-PC validation**.

---

## Repository map

```text
aetheris-platform/
├── gateway/                              API ingress and gateway boundary concerns
├── identity-service/                     Authentication and token lifecycle
├── user-service/                         User-domain service and persistence
├── audit-service/                        Audit/event consumption surface
├── dashboard/                            React + TypeScript operator UI
├── observability/                        Prometheus/Grafana/Loki/Tempo/OTel config
├── deploy/                               Kubernetes and Helm assets
├── contracts/                            Cross-service and external-runtime contracts
├── architecture/                         Runtime extraction/certification references
├── docker-compose.integration-external.yml  Certified external-runtime integration
├── tools/load_certified_ai_runtime.sh    Certified-runtime loading helper
├── docs/                                 Architecture, security, runbooks and scope docs
└── build-evidence/                       Repository-side validation evidence/contracts
```

Runtime-owned source directories such as `orchestrator-service/`, `workstation-agent/`, `aetheris-reasoning/` and `aetheris-quant/` intentionally live in the separate AI-runtime repository.

---

## What this project demonstrates in an interview

| Area | Discussion value |
|---|---|
| Backend engineering | API boundaries, DTOs, validation, persistence and failure behavior |
| Security | token lifecycle, authorization assumptions and secret hygiene |
| Distributed systems | gateway topology, Redis, RabbitMQ and asynchronous audit flow |
| Reliability | circuit breaking, safe retry decisions and failure containment |
| Observability | metrics, logs, traces and distributed debugging |
| DevOps | Docker Compose, Kubernetes, Helm and reproducible CI |
| AI systems — optional | cross-repository runtime boundaries, tool/model separation, policy, approvals and verification |
| Engineering communication | ADRs, runbooks, scope/maturity labeling and trade-off reasoning |

The recommended interview approach is to **defend the core platform first** and discuss the AI/operator extensions only when relevant to the role or interviewer.

---

## Documentation

### Reviewer / recruiter path

- [Portfolio scope & maturity](docs/portfolio-scope.md)
- [Demo guide](docs/demo-guide.md)
- [Interview guide](docs/interview-guide.md)
- [Architecture decisions](docs/architecture-decisions.md)
- [Architecture](docs/architecture.md)

### Deep engineering / governance path

- [Master roadmap](docs/master-roadmap.md)
- [Master build specification](docs/master-build-spec.md)
- [Verification checklist](docs/verification-checklist.md)
- [First-boot runbook](docs/first-boot-runbook.md)
- [Versioning and release classes](docs/versioning.md)
- [Release-readiness checklist](docs/release-readiness.md)

---

## Release status

A repository pre-PC candidate named `v0.1.0-pre-pc` has been prepared but not published. The platform is licensed under the **Apache License 2.0**; publication still requires the exact release-candidate verification and truth-boundary checks documented in the release-readiness checklist.

## License

Aetheris Platform is licensed under the **Apache License 2.0**. See [`LICENSE`](LICENSE) for the full terms. Third-party dependencies and incorporated third-party materials remain subject to their respective licenses.

Licensing does not imply physical-PC validation, production activation, registry publication, or live-money execution. The physical-machine status remains `BLOCKED_PENDING_HARDWARE`.

---

## Contributing

Read **[CONTRIBUTING.md](CONTRIBUTING.md)** before changing code, especially identity, policy, workstation, trading or deployment surfaces.

Security-sensitive findings should follow **[SECURITY.md](SECURITY.md)** and should never include secrets in a public issue.

---

<div align="center">

### Build. Secure. Observe. Deploy. Verify.

**Built by Poojana Kaveesh Sellahewa**

</div>