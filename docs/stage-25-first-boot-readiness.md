# Stage 25 — First-Boot Readiness & External AI Runtime Contract

## Scope

Stage 25 prepares the **Aetheris platform with an independently owned AI runtime** for the owner's future physical PC. Platform-owned services remain in `teldigi5-wq/aetheris-platform`; AI runtime ownership is now anchored to `teldigi5-wq/aetheris-ai-runtime` at certified canonical SHA `68af39a1115a7330020c18b6e2cb601e66b8f22f`.

The four runtime-owned source roots are `orchestrator-service/`, `aetheris-quant/`, `aetheris-reasoning/`, and `workstation-agent/`. This adaptation stage does **not** delete those roots from the platform repository yet. Final source deletion remains blocked until the platform external-integration proof passes and is certified.

This stage does not claim that the owner's PC exists or that WSL2, Docker Desktop, GPU acceleration, local AI performance, thermals, microphone/voice latency, workstation activation, or complete local end-to-end execution has passed.

## Repository-side acceptance criteria

Stage 25 preparation passes only when:

1. the versioned first-boot contract loads successfully;
2. required Stage 22/23 platform safety scripts remain present;
3. platform-owned gateway, identity, user, audit and dashboard surfaces remain present;
4. Java, Maven, Node and platform Python pins match the contract;
5. `docker-compose.core.yml` contains only the required platform service/port inventory;
6. `docker-compose.integration-external.yml` binds orchestrator port `8090` to an exact-revision external runtime image and does not locally build any runtime-owned root;
7. `architecture/ai-runtime-certification-reference.json` pins `teldigi5-wq/aetheris-ai-runtime` to `68af39a1115a7330020c18b6e2cb601e66b8f22f` with canonical CI status `6_OF_6_SUCCESS`;
8. Stage 25 tests pass;
9. CI reports `physical_pc_status: NOT_TESTED`;
10. no high-impact workstation or financial boundary is weakened;
11. readiness evidence is uploaded from CI.

## Prepared before the PC

- pinned platform toolchain expectations;
- dependency and Maven-tree evidence for platform-owned services;
- exact certified AI-runtime repository/SHA reference;
- external runtime Compose integration contract;
- Windows/WSL2/Docker acceptance sequence;
- workstation-agent safety validation procedure against the independent runtime repository;
- deterministic build comparison procedure;
- platform service plus external-orchestrator health procedure;
- observability procedure;
- explicit local-AI benchmark boundary;
- paper/testnet-only quant boundary.

## Blocked until the physical PC exists

- real Windows host inventory;
- firmware virtualization evidence;
- owner-PC WSL2 status;
- Docker Desktop daemon validation;
- cross-machine artifact hash comparison;
- real workstation-agent activation checks;
- GPU/driver/model-loading benchmarks;
- complete platform + external-runtime health;
- thermals and sustained-operation measurements.

A hosted CI pass is not a substitute for any item in this blocked list.

## First owner-PC command

From a clean checkout of the platform repository on the actual machine:

```powershell
python scripts/first_boot_preflight.py --mode host --evidence-dir build-evidence\physical-pc
```

The output must be retained even if it fails. Fix the measured blocker and rerun rather than editing evidence to look successful. Runtime-specific owner-PC checks must use the independently certified runtime repository/revision, not a stale platform copy.
