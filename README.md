# Aetheris Platform

> A local-first, owner-controlled AI operating platform for **Syntra** — combining orchestration, security, agent execution, observability, trading intelligence, PC/system engineering, and advanced reasoning into one evolving platform.

[![Build](https://github.com/teldigi5-wq/aetheris-platform/actions/workflows/build.yml/badge.svg)](https://github.com/teldigi5-wq/aetheris-platform/actions/workflows/build.yml)
[![CI](https://github.com/teldigi5-wq/aetheris-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/teldigi5-wq/aetheris-platform/actions/workflows/ci.yml)
[![CodeQL](https://github.com/teldigi5-wq/aetheris-platform/actions/workflows/codeql.yml/badge.svg)](https://github.com/teldigi5-wq/aetheris-platform/actions/workflows/codeql.yml)

## Current master-roadmap status

**Stage 30 / 34 implemented**

The canonical 34-stage roadmap is tracked in [`docs/master-roadmap.md`](docs/master-roadmap.md). The repository has reached Stage 30: **Advanced reasoning, verification and decision systems**.

### Implemented platform areas

- Java service platform foundations: gateway, identity, user and audit surfaces
- dashboard and observability foundations
- governed Syntra/Aetheris agent-control architecture
- security, audit, CI and CodeQL governance
- PC-care and system-engineering foundations
- trading intelligence and controlled-execution foundations
- Stage 30 reasoning-control package with confidence/uncertainty handling, deterministic routing, verifier/critic checks, simulation gates, decision ledger, source trust, contradiction detection, dependency graphs, temporal checks, owner-rule compilation and change-impact analysis

## Architecture

```text
OWNER
  |
  v
SYNTRA
  |  voice / text / approvals / live progress
  v
AETHERIS CORE
  |  planning / rules / routing / permissions / memory
  |
  +--> Specialist agents
  +--> Model router
  +--> Reasoning + verification control plane
  +--> Computer / browser / IDE / files / APIs
  +--> Security / audit / observability / recovery
  +--> Trading / system engineering / automation modules
  |
  v
VERIFIED RESULTS -> SYNTRA -> OWNER
```

Aetheris treats models as replaceable workers rather than policy authority. High-consequence actions are gated by deterministic rules, verification and explicit owner control.

## Repository structure

```text
.github/             GitHub Actions, Dependabot and repository automation
aetheris-quant/      Trading intelligence and controlled execution foundation
aetheris-reasoning/  Stage 30 reasoning, verification and decision controls
audit-service/       Audit and traceability service
dashboard/           Web dashboard
deploy/              Deployment assets and configuration
docs/                Architecture, governance and roadmap documentation
gateway/              API gateway
identity-service/    Identity and authentication service
observability/       Monitoring/telemetry assets
user-service/        User/profile service
```

## Stage 30 — reasoning, verification and decision systems

Stage 30 lives under [`aetheris-reasoning/`](aetheris-reasoning/) and deliberately separates **policy and verification authority** from model output.

The foundation includes:

- **Meta-Reasoning Engine** — routes work across deterministic, local-model, specialist, research, simulation, council and owner-approval paths
- **Uncertainty & Confidence Engine** — triggers verification when uncertainty or consequence is high
- **Verifier/Critic Layer** — catches missing verification and recovery steps
- **Simulation Gate** — requires simulation/dry-run treatment for risky side effects
- **Decision Ledger** — append-only SHA-256 hash-chained decision records
- **Policy Compiler** — deterministic owner-rule normalization with conflict detection
- **Task Dependency Graph** — prerequisites, blockers, ready work and cycle detection
- **Change Impact Analyzer** — affected services, tests and rollback needs
- **Data Lineage / Source Trust** — source-classification and cross-check requirements
- **Contradiction Detector** — surfaces conflicting claims instead of flattening disagreement
- **Temporal Intelligence** — validity windows and change detection

See [`docs/stage-30-reasoning.md`](docs/stage-30-reasoning.md).

## Next roadmap stage

### Stage 31 — Digital twins, proactive intelligence and self-healing

Planned Stage 31 work includes:

- project digital twin
- PC digital twin
- owner workspace model
- proactive issue detection
- goal/priority management
- bounded self-healing runtime
- staged safe-update manager with health checks and rollback
- automatic model/runtime benchmarking

Stage 31 will preserve the same truth boundary: the system must not claim real machine measurements, repairs or deployment outcomes unless they were actually observed and verified.

## Development model

`main` is the **canonical stable line**. New roadmap work should start from the current `main` tip and return through reviewable pull requests.

The older `feature/syntra-aetheris-foundation-v2` branch remains a legacy development history while reconciliation/retirement is completed. **Do not start new roadmap stages from that branch.** The merged `stage-30-reasoning-foundation` branch is historical and should not be reused.

Dependabot branches are generated automatically. Review their pull requests individually rather than merging dependency branches directly into roadmap work.

## CI and security

Repository-side governance currently includes:

- Java build and tests
- identity security tests
- dashboard build
- Stage 30 reasoning tests
- CodeQL analysis
- Dependabot update proposals

GitHub-hosted branch protection/rulesets are a separate repository-administration control. Repository-side CI does not substitute for those account/repository settings.

## Safety principles

- Owner authority is final.
- STOP/PAUSE/TAKE CONTROL must outrank background or model work.
- High-impact actions require policy evaluation and approval.
- Private/protected work must not silently route off-device.
- Models and agents cannot silently bypass deterministic controls.
- Important side effects require verification and an audit trail.
- Completion must never be fabricated; unverified work must be labelled unverified.
- Trading signals are hypotheses, not guaranteed profit.
- Live financial execution remains governed by deterministic risk limits and configured owner approval.
- Unauthorized or destructive cybersecurity activity is outside the intended operating model.

## Local development

### Requirements

- Java 21
- Maven
- Node.js 22+ (CI currently validates the dashboard on modern Node runtimes)
- Python 3.11+ for `aetheris-reasoning`
- Docker Desktop / Compose when running the full service stack

### Java platform

```bash
mvn -B test
mvn -B -DskipTests package
```

### Dashboard

```bash
cd dashboard
npm install
npm run build
```

### Stage 30 reasoning tests

```bash
cd aetheris-reasoning
python -m unittest discover -s tests -v
```

### Docker stack

```bash
docker compose up --build
```

Use the repository environment examples and service documentation before enabling integrations. Never commit secrets or private API credentials.

## Documentation

- [`docs/master-roadmap.md`](docs/master-roadmap.md) — canonical 34-stage status
- [`docs/stage-30-reasoning.md`](docs/stage-30-reasoning.md) — Stage 30 implementation contract
- [`SECURITY.md`](SECURITY.md) — security reporting and expectations
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — contribution workflow

## Status and truth boundary

Repository/CI success is not the same thing as physical-PC validation. Hardware telemetry, driver behavior, thermals, GPU performance, real desktop control and machine repair remain unverified until exercised on the owner's actual target PC.

## Owner

**Poojana Kaveesh**  
BSc (Hons) in Information Technology — AI-focused development path  
Repository: `teldigi5-wq/aetheris-platform`
