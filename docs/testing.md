# Testing Strategy

Aetheris treats testing as engineering evidence rather than a checkbox. Repository claims must remain tied to repeatable validation, and hosted CI must not be presented as proof of physical-PC behavior.

## Current automated layers

The canonical development line validates multiple independent surfaces rather than relying on one monolithic test job.

### Java platform and orchestrator

The Build workflow runs tests for the gateway, user service, identity service, audit service and orchestrator. Security-sensitive authentication, authorization, refresh-token and service-boundary behavior remain part of the Java test surface.

### Workstation-agent safety

Windows packaging checks validate the workstation-agent scripts and generated unsigned package. CI keeps the package loopback-only, without unrestricted shell capability, without normal-runtime administrator requirements, without production activation and without live-money/withdrawal/transfer authority.

### Dashboard

The dashboard uses a frozen dependency install and production build. Frontend success is treated as build evidence only; it does not prove physical-machine responsiveness, 144 Hz behavior or complete desktop UX.

### Release integrity

Repository release gates include:

- sensitive-file and secret checks;
- persistence/schema guardrails;
- deterministic source SBOM and release-manifest generation;
- compatibility-contract freeze;
- dependency-lock verification;
- two-pass reproducible-build artifact hashing.

### Stage-specific regression suites

The canonical roadmap additionally runs dedicated regression suites for:

- Stage 30 — reasoning, confidence, verification, simulation, policy and decision controls;
- Stage 31 — digital twins, proactive intelligence and bounded recovery planning;
- Stage 32 — automation, observability and emergency control;
- Stage 33 — cross-system governance, scoped approvals and completion truth;
- Stage 34 — canonical master-build specification integrity.

Where appropriate, stage suites are repeated in the same workflow to catch hidden state coupling.

### Static/security analysis

CodeQL analyzes the Java and JavaScript/TypeScript surfaces. Dependency audits and pinned/locked dependencies complement static analysis; none of these mechanisms replaces runtime security review.

## Evidence boundaries

A green repository pipeline demonstrates that the checked source revision satisfies its configured automated gates. It does **not** prove:

- WSL2 or Docker Desktop behavior on the owner's target Windows machine;
- NVIDIA/RTX 4050 acceleration, VRAM behavior, model throughput or first-token latency;
- thermals, storage health or driver state;
- microphone, STT, TTS or wake-word quality on physical hardware;
- browser/phone control or real external-notification delivery;
- full local-stack recovery after real machine failures;
- production readiness or unrestricted autonomous execution.

Physical-machine status therefore remains `BLOCKED_PENDING_HARDWARE` until evidence is collected on the intended system.

## Test-development rule

For every meaningful change:

1. identify the affected contract and risk boundary;
2. add or update the smallest deterministic test that can prove the intended behavior;
3. run the focused test before the full gate set;
4. run security/static checks when the affected surface requires them;
5. preserve rollback and compatibility evidence;
6. update documentation only after the final candidate passes.

A capability should only be described as automatically tested when a repeatable test exists and runs successfully. Manual verification, simulations and architecture documentation remain useful evidence, but they must be labelled separately from automated or physical validation.
