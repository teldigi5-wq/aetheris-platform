# Stage 31 — Digital twins, proactive intelligence and self-healing

Status: **implementation candidate — CI validation required before completion is claimed**.

Stage 31 extends the existing Aetheris orchestrator rather than creating a second autonomous runtime. The repository already contains proactive task/provider/approval scanning and evaluation services; this stage adds structured digital-twin contracts, deterministic prioritization, bounded recovery planning, safe-update state transitions and model benchmarking around those existing systems.

## Implemented candidate capabilities

### Project Digital Twin

`ProjectDigitalTwin` captures repository/project state including revision, branch, dependencies, bugs, tasks, goals, CI state, documentation state, failed-test count, deployment health, dependency drift, quota and evidence time.

### PC Digital Twin

`PcDigitalTwin` models hardware profile, drivers, applications, services, failed services, network profile, disk/memory/temperature signals and known-good configuration. Evidence is explicitly labelled as synthetic, observed or physically verified.

A synthetic snapshot is forbidden from claiming physical verification. Until real owner-PC evidence exists, repository/CI status remains `BLOCKED_PENDING_HARDWARE`.

### Owner Workspace Model

`OwnerWorkspaceModel` keeps project focus, career/study areas, approved routines, tool preferences, local-first preference and Zero-Cost preference in a structured owner-visible contract. It does not contain credentials or secret values.

### Proactive Intelligence

The Stage 31 detector can surface deterministic issues for:

- failed CI and tests;
- incomplete documentation;
- dependency drift;
- unhealthy deployment evidence;
- quota exhaustion;
- low disk space;
- resource pressure;
- failed services;
- workspace/project drift;
- missing physical-PC validation.

Synthetic PC telemetry stays labelled synthetic and cannot become a verified host incident.

### Goal Manager / Priority Brain

Goals are ranked deterministically using urgency, importance, dependency-unblocking value, owner preference and risk. Repeated evaluation of the same inputs returns the same ordering.

### Bounded Self-Healing Planner

Stage 31 plans recovery eligibility but deliberately contains no host-side execution engine. A recovery is only marked auto-eligible when it is reversible, pre-approved, within approved bounds and non-privileged. Financial, destructive, user-data and security-control actions are blocked from Stage 31 autonomy.

### Safe Auto-Update Manager

Updates follow a fail-closed lifecycle:

`PROPOSED -> STAGED -> CANARY_RUNNING -> HEALTHY -> PROMOTED`

A failed canary becomes:

`CANARY_RUNNING -> ROLLBACK_REQUIRED -> ROLLED_BACK`

Promotion is impossible without verified health evidence.

### Automatic Model Benchmarking

The benchmark engine compares model candidates using quality, first-token latency, throughput, failure rate, VRAM use and estimated cost. Zero-Cost Mode and private-only routing are hard eligibility filters rather than soft preferences.

## API surface

The orchestrator exposes read/evaluation-oriented Stage 31 endpoints under `/api/v1/stage31` for:

- digital-twin assessment;
- recovery planning;
- update-state transitions;
- model benchmark ranking.

These endpoints evaluate plans and evidence. They do not directly restart services, delete files, change drivers, trade live money, modify security controls or perform privileged machine mutations.

## Validation target

`.github/workflows/stage31-digital-twins.yml` runs `Stage31FoundationTest` twice on pinned Java/GitHub Action versions to detect regressions and hidden state coupling.

Stage 31 should only be marked complete in the master roadmap after this workflow and the canonical repository gates pass.
