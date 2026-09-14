# Stage 28 — PC Care & System Engineering

Date: 2026-09-14

## Objective

Stage 28 introduces the PC-care decision layer for Syntra/Aetheris without pretending the owner's physical PC already exists or allowing CI to modify any host.

The Stage 28 repository milestone is therefore **diagnosis and remediation planning**, not autonomous repair execution.

## Existing foundation preserved

The workstation agent already exposes a deliberately narrow host capability surface, including system telemetry and process-status inspection, while blocking arbitrary host capabilities. Stage 28 does not weaken that design and does not add process-kill, registry, driver, service-control, reboot, file-deletion or security-control mutation commands.

## Stage 28 components

- `configs/stage28/pc-care-policy.json` — health thresholds and hard mutation boundaries.
- `scripts/stage28/pc_care_diagnostics.py` — deterministic standard-library-only diagnostic planner.
- `tests/fixtures/stage28/` — synthetic healthy, warning and critical snapshots.
- `tests/test_stage28_pc_care.py` — fail-closed policy, source and determinism tests.
- `.github/workflows/stage28-pc-care.yml` — two-pass deterministic Stage 28 certification.

## Diagnostic model

The pre-PC model evaluates four generic health signals:

| Signal | Warning | Critical | Direction |
| --- | ---: | ---: | --- |
| CPU utilization | 85% | 95% | high is bad |
| Memory utilization | 85% | 95% | high is bad |
| Disk free space | 15% | 5% | low is bad |
| Temperature | 85°C | 95°C | high is bad |

These are engineering defaults for the simulation contract, not measurements from the owner's future PC. Hardware-specific thresholds can be reviewed after the actual CPU/GPU/storage platform is known.

## Safety model

Stage 28 is intentionally recommendations-only before hardware validation.

The following remain disabled:

- autonomous host mutation;
- process termination;
- file deletion;
- Registry writes;
- driver changes;
- service restart/control;
- shutdown/reboot;
- network configuration changes;
- security-control changes.

Any later mutating remediation must pass owner-policy evaluation and explicit approval before the workstation adapter may receive a narrowly scoped command.

## CI truth boundary

Hosted CI accepts only snapshots with:

`source = synthetic_fixture`

A snapshot claiming `owner_physical_pc` is rejected by the Stage 28 pre-PC verifier. CI reports therefore cannot be presented as measurements from the owner's machine.

Every certified report states:

```text
physicalPcStatus = BLOCKED_PENDING_HARDWARE
simulationOnly = true
recommendationsOnly = true
hostMutationAttempted = false
networkRequired = false
shellExecutionRequired = false
```

## Determinism

The planner emits canonical sorted JSON without timestamps, random values or machine-derived data. CI generates every fixture report twice and uses byte comparison to prove deterministic output.

## When the PC arrives

The safe next step is **not** to enable autonomous repair. First, Stage 25 physical-machine onboarding will validate the real Windows/WSL/Docker/toolchain environment. Then the signed workstation agent can provide measured telemetry through its existing narrow capability surface. Stage 28 recommendations can be evaluated against that evidence.

Only after real host validation should later work consider narrowly scoped remediation adapters, and those adapters must preserve explicit owner approval, evidence journaling, rollback and emergency STOP semantics.

## Stage 28 completion criteria

Repository-side Stage 28 is complete when:

1. policy hard boundaries validate fail-closed;
2. healthy, warning and critical synthetic cases are covered;
3. non-synthetic CI input is rejected;
4. no repair is executed;
5. reports are deterministic across two passes;
6. Stage 28 CI is green;
7. physical-PC status remains `BLOCKED_PENDING_HARDWARE`.

Physical PC care remains operationally pending until the actual machine exists.
