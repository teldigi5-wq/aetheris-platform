# Stage 25 — First-Boot Readiness & Environment Contract

## Scope

Stage 25 prepares the **full `feature/syntra-aetheris-foundation-v2` codebase** for the owner's future physical PC. It includes the orchestrator, workstation agent, Stage 22 release hardening and Stage 23 compatibility freeze in the readiness contract.

This stage does not claim that the owner's PC exists or that WSL2, Docker Desktop, GPU acceleration, local AI performance, thermals, microphone/voice latency or complete local end-to-end execution has passed.

## Repository-side acceptance criteria

Stage 25 preparation passes only when:

1. the versioned first-boot contract loads successfully;
2. required Stage 22/23 safety scripts remain present;
3. orchestrator and workstation-agent surfaces remain present;
4. Java, Maven, Node and Python pins match the contract;
5. the committed Compose service/port inventory matches the contract, including orchestrator port `8090`;
6. the Stage 25 tests pass;
7. CI reports `physical_pc_status: NOT_TESTED`;
8. no high-impact workstation or financial boundary is weakened;
9. readiness evidence is uploaded from CI.

## Prepared before the PC

- pinned toolchain expectations;
- dependency and Maven-tree evidence;
- Windows/WSL2/Docker acceptance sequence;
- workstation-agent safety validation procedure;
- deterministic build comparison procedure;
- core service/orchestrator health procedure;
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
- complete local-stack runtime health;
- thermals and sustained-operation measurements.

A hosted CI pass is not a substitute for any item in this blocked list.

## First owner-PC command

From a clean checkout on the actual machine:

```powershell
python scripts/first_boot_preflight.py --mode host --evidence-dir build-evidence\physical-pc
```

The output must be retained even if it fails. Fix the measured blocker and rerun rather than editing evidence to look successful.
