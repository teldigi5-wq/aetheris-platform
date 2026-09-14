# Aetheris Platform

Aetheris is a local-first, owner-controlled AI operating platform for Syntra: orchestration, reasoning, agents, tools, automation, security, telemetry, recovery, and specialist systems in one evolving ecosystem.

## Current master-roadmap status

**Stage 30 / 34 implemented**

The canonical 34-stage product blueprint is tracked in [`docs/master-roadmap.md`](docs/master-roadmap.md). The repository has now reached Stage 30: **Advanced reasoning, verification and decision systems**.

Current high-level capabilities include:

- service-oriented Java platform foundations;
- identity/security and audit components;
- dashboard and observability foundations;
- governed Syntra/Aetheris agent-control concepts;
- PC-care and system-engineering foundations;
- trading intelligence and controlled execution foundations;
- Stage 30 reasoning-control primitives: confidence/uncertainty handling, deterministic routing, verifier/critic checks, simulation gates, decision ledger, source trust, contradiction detection, task dependency graphs, temporal checks, owner-rule compilation, and change-impact analysis.

## Stage 30 reasoning-control foundation

The Stage 30 package lives under [`aetheris-reasoning/`](aetheris-reasoning/) and deliberately keeps policy/verification authority outside model output. High-consequence work escalates, high-risk side effects route through simulation, and unsupported policy language fails closed for owner review rather than being guessed.

See [`docs/stage-30-reasoning.md`](docs/stage-30-reasoning.md) for the implemented contract.

## Next stage

**Stage 31 — Digital twins, proactive intelligence and self-healing**

Stage 31 will add structured project/PC state models, proactive issue detection, priority management, health-driven recovery, safe staged updates, and model/runtime benchmarking while preserving the same owner-control and truth-boundary principles.

## Development and release model

- `main` is the stable canonical line.
- New roadmap work should branch from the current `main` tip and return through reviewable pull requests.
- Merged stage branches are historical/temporary and should not be reused for future stages.
- Dependabot branches are automated dependency-update branches and should be reviewed through their pull requests instead of manually mixed into roadmap work.
- The older `feature/syntra-aetheris-foundation-v2` line is retained only as a legacy development history until reconciliation/retirement is complete; new roadmap work must not branch from it.

## CI and security

Repository-side governance includes:

- Java builds and tests;
- identity security tests;
- dashboard builds;
- Stage 30 reasoning tests;
- CodeQL analysis;
- Dependabot update proposals.

GitHub-hosted branch protection/rulesets are a separate repository-administration control. Repository-side CI does not substitute for those GitHub settings.

## Safety boundaries

Aetheris is designed around owner authority, reversibility, explicit approvals for high-impact actions, protected handling of private data, and verifiable completion. Models and agents may recommend actions but must not silently bypass deterministic controls.

For trading-related functionality, signals are hypotheses rather than promises; live execution remains governed by deterministic risk limits and owner policy.

## Local development

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

### Stage 30 reasoning package

```bash
cd aetheris-reasoning
python -m unittest discover -s tests -v
```

## Repository

Owner: **Poojana Kaveesh**  
Repository: **teldigi5-wq/aetheris-platform**
