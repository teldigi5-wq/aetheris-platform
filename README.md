<div align="center">

# ⚡ Aetheris Platform

### Build. Secure. Observe. Orchestrate. Govern. Verify.

**A long-term platform engineering and local-AI foundation focused on secure services, deterministic verification, owner-controlled automation, cross-system governance, emergency control, trading safeguards, system engineering, digital twins and advanced reasoning.**

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-TypeScript-61DAFB?style=for-the-badge&logo=react&logoColor=111827)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-Helm-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)

`Java 21` • `Spring Boot` • `React` • `PostgreSQL` • `Redis` • `RabbitMQ` • `Prometheus` • `Grafana` • `OpenTelemetry` • `Kubernetes` • `Helm` • `Python verification tooling`

</div>

---

## 🎯 What Aetheris demonstrates

Aetheris is an engineering portfolio and experimentation platform that grows one capability at a time instead of collecting disconnected demos. The repository includes API platform work, identity and authorization, distributed data services, observability, resilience, deployment automation, AI-agent governance, structured owner policy, scoped approvals, deterministic release evidence, repository governance, PC/system-engineering controls, fail-closed trading intelligence, advanced reasoning/verification, digital-twin/proactive control and emergency-aware automation foundations.

> **Portfolio goal:** every important capability should be explainable in an interview, visible in code, and backed by reproducible evidence rather than unsupported claims.

---

## ✅ Current repository milestone — Stage 33 / 34

The canonical development line has reached **Stage 33 — Governance, approvals and cross-system policy** on the repository/CI side.

Current repository status: **`PRE_PC_HARDENED + STAGE33_CROSS_SYSTEM_GOVERNANCE`**.

Current physical-machine status: **`BLOCKED_PENDING_HARDWARE`**.

**Physical-PC validation remains pending.** Hosted CI and repository evidence do not prove that WSL2, Docker Desktop, GPU acceleration, local-model performance, thermals, storage health, external notification delivery, browser/phone control or the complete local stack work on the owner's future physical machine.

### Stage 33 result

Stage 33 adds one deterministic governance lifecycle across systems: `UNDERSTAND → PLAN → CHECK RULES → ASSESS RISK → SIMULATE/PREVIEW → APPROVE WHEN REQUIRED → EXECUTE → VERIFY → RECORD → LEARN → REPORT`.

It adds explicit policy precedence and conflict detection, Zero-Cost hard blocking, Private-mode scoped overrides, risk escalation, simulation gates, exact-scope expiring approvals, one-time approval replay protection, Stage 32 emergency-control precedence and verified-vs-unverified completion truth. A preflight `ALLOW` means only execution eligibility; success cannot be claimed without verification evidence.

The stage adds no unrestricted shell, privileged executor, new HTTP route, database table, production-activation authority or live-money authority.

### Stage 32 result

Stage 32 adds deterministic workflow composition; resource-, retry-, user-load-, energy- and deadline-aware scheduling; normalized event watcher contracts with dedup/debounce; notification/focus policy; daily and end-of-day briefings; automation timeline and audit filtering; short-lived owner-authenticated replay-safe remote authorization; artifact provenance/retention; runtime checkpoints; and emergency precedence `STOP > TAKE_CONTROL > PAUSE > NORMAL`.

The stage extends the existing orchestrator rather than creating a second runtime. It adds no unrestricted shell, arbitrary remote command channel, new HTTP endpoint, database table, live-money authority or production-activation authority.

### Stage 31 result

Stage 31 adds structured project, PC and owner-workspace digital twins; deterministic proactive issue detection; goal/priority ranking; bounded self-healing planning; safe canary/update/rollback state transitions; automatic model benchmarking; and `/api/v1/stage31` evaluation endpoints.

The stage remains deliberately fail-closed: it evaluates and plans but does not add an unrestricted privileged host executor. Destructive/user-data, financial and security-control autonomy stays blocked, and synthetic PC evidence cannot become physical-machine validation.

### Stage 30 result

Stage 30 adds a deterministic reasoning-control layer under [`aetheris-reasoning/`](aetheris-reasoning/) rather than treating one model as infallible. The foundation includes meta-reasoning route selection, uncertainty/confidence assessment, verifier/critic checks, simulation gates, tamper-evident decision records, source trust/lineage, contradiction and temporal checks, deterministic owner-rule compilation, dependency graphs and change-impact analysis.

### Stage 29 result

Stage 29 adds fail-closed trading-intelligence policy and verification. Trading outputs remain proposals and hypotheses rather than guaranteed profit. Live-money execution, withdrawals and transfers remain outside the default trusted path.

### Repository governance

The intended long-lived branch model remains:

- **`main`** — stable/release history.
- **`feature/syntra-aetheris-foundation-v2`** — canonical active development line.

Stages 30–33 continue the mature development history without discarding the Stage 24–29 hardening work. Temporary stage branches are review branches, not future canonical bases.

GitHub administrative branch protection/rulesets remain a separate account/repository setting. Repository-side CI does not substitute for GitHub-hosted protection.

---

## 🌿 Development-line policy

All new PC-independent Syntra/Aetheris roadmap work continues from:

`feature/syntra-aetheris-foundation-v2`

Only reviewed promotion work should move toward `main`. New roadmap stages must branch from the current canonical development tip, not from stale merged stage branches.

---

## 🏗️ Architecture

```mermaid
flowchart LR
    U[Browser / Client] --> D[React Dashboard]
    D --> G[API Gateway]
    G --> I[Identity Service]
    G --> S[User Service]
    G --> A[Audit Service]
    G --> R[(Redis)]
    I --> P[(PostgreSQL)]
    S --> P
    S --> M[(RabbitMQ)]
    M --> A
    G --> O[Metrics / Traces]
    I --> O
    S --> O
    A --> O
    O --> OBS[Prometheus + Grafana + Loki + Tempo]
    EVT[Watchers + Event Triggers] --> AUTO[Scheduler + Automation Control]
    AUTO --> EMG[STOP / TAKE CONTROL / PAUSE]
    AG[Agent / Orchestrator Layer] --> GOV[Stage 33 Governance Lifecycle]
    RSN[Reasoning + Verification] --> GOV
    TWIN[Digital Twins + Proactive Intelligence] --> GOV
    AUTO --> GOV
    EMG --> GOV
    PC[PC Care Diagnostics] --> GOV
    TRD[Trading Intelligence] --> GOV
    GOV --> POL[Owner Policy + Scoped Approval + Evidence]
    POL --> G
    K[Kubernetes + Helm] --> D
    K --> G
    K --> I
    K --> S
    K --> A
```

### Core engineering domains

| Domain | What Aetheris implements |
|---|---|
| API platform | Gateway routing, protected endpoints, structured failures |
| Identity | JWT access tokens, refresh-token rotation, RBAC and scopes |
| Data | PostgreSQL persistence with shared service data |
| Distributed systems | Redis caching/rate limiting and RabbitMQ events |
| Resilience | Circuit breakers, retries, timeouts and fallbacks |
| Observability | Metrics, logs, distributed traces, automation timeline and audit exploration |
| Cloud native | Docker Compose, Kubernetes, Helm and health probes |
| AI/agents | Mission planning, safe tool registry, policy evaluation and orchestrator foundations |
| Reasoning | Confidence/uncertainty routing, verifier/critic checks, simulation gates and decision ledger |
| Digital twins | Structured project/PC/workspace state, proactive detection, priority management and safe recovery planning |
| Automation control | Workflow composition, event normalization, resource-aware scheduling, focus/notification policy and deterministic emergency preemption |
| Governance | Ordered cross-system lifecycle, policy precedence, risk escalation, scoped approval, mode enforcement and verification truth |
| Safety | Approval gates, dry-run controls, emergency control, incident replay and regression certification |
| Release integrity | Dependency lockdown, reproducible builds, contract freeze and deterministic evidence |
| Repository governance | Canonical development-line enforcement and release-promotion checks |
| PC care | Deterministic health classification and recommendations-only remediation planning before hardware validation |
| Trading | Fail-closed proposal generation, deterministic risk limits and disabled live-money defaults |

---

## 🧠 Cross-system reasoning and governance model

```text
Event / task / command / observed state
    |
    v
Watcher normalization + digital twins + reasoning
    |
    v
UNDERSTAND → PLAN → CHECK RULES → ASSESS RISK
    |
    v
SIMULATE / PREVIEW when required
    |
    v
APPROVE when required (exact scope + expiry + replay protection)
    |
    v
EXECUTION ELIGIBILITY
    |
    v
VERIFY → RECORD → LEARN → REPORT
```

Models may recommend actions, but they do not outrank deterministic owner policy, emergency control, Zero-Cost/Private-mode boundaries or verification requirements.

---

## 🔐 Security and owner-control model

Access tokens use signed JWTs containing identity, role and effective-scope claims. Refresh tokens are opaque, rotated on use, revocable and stored as hashes.

Repository safeguards keep critical pre-PC boundaries fail-closed: no production activation, no live-money execution, no withdrawals, no transfers, no unrestricted shell capability, no autonomous destructive PC repair and no administrative bypass is considered validated by hosted CI.

Stage 30 adds confidence, verification, simulation and owner-approval routing. Stage 31 adds bounded self-healing eligibility and explicit digital-twin evidence labels. Stage 32 adds replay-safe short-lived remote authorization, runtime checkpoints and deterministic STOP/TAKE CONTROL/PAUSE precedence. Stage 33 unifies those controls under ordered governance, exact-scope expiring approvals, rule-conflict detection and verified completion truth.

---

## 🧪 Verification and CI

The canonical development line uses pinned GitHub Action revisions and deterministic validation where practical. Current gates include:

- Java service tests including the orchestrator;
- workstation-agent packaging and safety checks;
- dashboard frozen dependency install/build;
- release hardening and deterministic evidence;
- compatibility contract freeze;
- dependency lockdown and reproducible-build verification;
- Stage 25–29 stage-specific validation;
- Stage 30 reasoning tests, repeated to catch hidden state coupling;
- Stage 31 digital-twin and bounded-recovery tests, repeated to catch hidden state coupling;
- Stage 32 automation, observability and emergency-control tests, repeated to catch hidden state coupling;
- Stage 33 governance and scoped-approval tests, repeated to catch hidden state coupling;
- CodeQL analysis for Java and JavaScript/TypeScript.

Stage 33 intentionally adds no HTTP/database contract surface, so the frozen compatibility contract remains unchanged. CI success is repository evidence, not proof of physical-machine behavior.

---

## 📈 Observability and resilience

The platform contains Prometheus/Grafana/Loki/Tempo/OpenTelemetry integration, structured health checks and resilience patterns such as timeouts, retries for safe reads, circuit breakers and controlled fallbacks.

Stage 31 adds deterministic digital-twin health/state interpretation and bounded recovery/update planning. Stage 32 adds normalized watcher events, timeline/audit reconstruction, resource/retry/deadline policy and emergency/runtime checkpoints. Stage 33 makes policy decisions and completion truth explicit and owner-auditable. Runtime performance on the owner's physical hardware remains intentionally unclaimed until physical validation exists.

---

## 🚀 Development quick start

Repository definitions include Docker Compose, Helm and service-level development workflows. Use the first-boot and validation runbooks before treating any local environment as validated:

- [`docs/first-boot-runbook.md`](docs/first-boot-runbook.md)
- [`docs/stage-25-first-boot-readiness.md`](docs/stage-25-first-boot-readiness.md)
- [`docs/stage-26-safety-certification.md`](docs/stage-26-safety-certification.md)
- [`docs/stage-27-repository-governance.md`](docs/stage-27-repository-governance.md)
- [`docs/stage-28-pc-care-system-engineering.md`](docs/stage-28-pc-care-system-engineering.md)
- [`docs/stage-29-trading-intelligence-execution.md`](docs/stage-29-trading-intelligence-execution.md)
- [`docs/stage-30-reasoning.md`](docs/stage-30-reasoning.md)
- [`docs/stage-31-digital-twins-self-healing.md`](docs/stage-31-digital-twins-self-healing.md)
- [`docs/stage-32-automation-observability-emergency-control.md`](docs/stage-32-automation-observability-emergency-control.md)
- [`docs/stage-33-governance-approvals.md`](docs/stage-33-governance-approvals.md)
- [`docs/master-roadmap.md`](docs/master-roadmap.md)
- [`docs/github-branch-protection.md`](docs/github-branch-protection.md)

### Stage 30 tests

```bash
cd aetheris-reasoning
python -m unittest discover -s tests -v
```

### Stage 31 tests

```bash
mvn -B -f orchestrator-service/pom.xml -Dtest=Stage31FoundationTest test
```

### Stage 32 tests

```bash
mvn -B -f orchestrator-service/pom.xml -Dtest=Stage32FoundationTest test
```

### Stage 33 tests

```bash
mvn -B -f orchestrator-service/pom.xml -Dtest=Stage33GovernanceTest test
```

The exact commands appropriate for full local execution depend on the machine and validation stage. The project intentionally does not claim successful owner-PC execution before that machine is available and tested.

---

## 🗺️ Engineering progression

Recent milestones include:

- **Stage 21** — physical-target pilot contract and owner-policy boundaries;
- **Stage 22** — release hardening, schema/security evidence and merge readiness;
- **Stage 23** — frozen compatibility contract;
- **Stage 24** — dependency lockdown and reproducible-build verification;
- **Stage 25** — deterministic first-boot readiness bundle and physical-PC truth boundary;
- **Stage 26** — deterministic safety certification plus evidence-integrity controls;
- **Stage 27** — canonical branch governance and release-promotion gate;
- **Stage 28** — deterministic PC-care diagnostics and safe remediation planning;
- **Stage 29** — fail-closed trading intelligence and execution-policy foundation;
- **Stage 30** — advanced reasoning, verification and decision-control foundation;
- **Stage 31** — digital twins, proactive intelligence and bounded self-healing foundation;
- **Stage 32** — automation, observability and deterministic emergency-control foundation;
- **Stage 33** — cross-system governance, scoped approvals and verified completion truth.

### Next: Stage 34

**Master build prompt** — consolidate the implemented architecture, safety invariants, build order, validation gates, physical-PC truth boundary and owner-control rules into the final reproducible master build specification.

Physical execution milestones remain blocked until suitable owner hardware exists.

---

## 📚 Documentation

- [Architecture](docs/architecture.md)
- [Interview talking points](docs/interview-guide.md)
- [Token flow and threat model](docs/security/token-flow.md)
- [Resilience runbook](docs/resilience.md)
- [Kubernetes + Helm runbook](docs/kubernetes.md)
- [First-boot runbook](docs/first-boot-runbook.md)
- [Stage 25 first-boot readiness](docs/stage-25-first-boot-readiness.md)
- [Stage 26 safety certification](docs/stage-26-safety-certification.md)
- [Stage 27 repository governance](docs/stage-27-repository-governance.md)
- [Stage 28 PC care & system engineering](docs/stage-28-pc-care-system-engineering.md)
- [Stage 29 trading intelligence & execution](docs/stage-29-trading-intelligence-execution.md)
- [Stage 30 reasoning & verification](docs/stage-30-reasoning.md)
- [Stage 31 digital twins & bounded self-healing](docs/stage-31-digital-twins-self-healing.md)
- [Stage 32 automation, observability & emergency control](docs/stage-32-automation-observability-emergency-control.md)
- [Stage 33 governance & approvals](docs/stage-33-governance-approvals.md)
- [Master roadmap](docs/master-roadmap.md)
- [GitHub branch protection target](docs/github-branch-protection.md)

---

## 💡 Design rule

The development stack is designed around **no mandatory recurring AI or platform subscription fee**. Open-source and locally controllable components are the default; external/cloud services remain optional and policy-governed.

---

<div align="center">

### From platform fundamentals to policy-governed local AI infrastructure.

**Built by Poojana Kaveesh Sellahewa**

</div>
