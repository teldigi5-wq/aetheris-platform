# Stage 26 — Safety Scenario Certification & Policy Regression Guard

Date: 2026-09-13

## Objective

Stage 26 adds a deterministic, offline certification layer over safety controls that already exist across the Syntra/Aetheris foundation. It does **not** replace Stage 9 mission dry-runs, Stage 15 incident replay, Stage 15 chaos rehearsal, the owner-policy engine, workstation-agent safety checks, or the Stage 25 physical-PC truth boundary.

Its purpose is to catch accidental weakening of those controls before a change is accepted.

## Why this is a new layer

The platform already contains runtime-oriented simulation and replay features:

- Stage 9 evaluates mission plans without creating tasks, tools, external actions or financial execution.
- Stage 15 reconstructs incident timelines as read-only replay.
- Stage 15 chaos rehearsal allows only explicitly isolated simulation faults.
- The owner policy engine denies incompatible ZERO_COST and PRIVATE actions and requires approval for high-impact operations.
- The safe tool registry assigns risk and capabilities before policy evaluation.
- The workstation-agent build gate keeps unsigned packages non-production and keeps financial execution boundaries disabled.

Stage 26 is different: it is a **regression certification harness**. It checks these source contracts together, produces deterministic evidence, and fails closed if a required safety marker disappears or a forbidden capability appears.

## Certified scenarios

The initial suite certifies the following boundaries:

1. ZERO_COST mode blocks billable execution.
2. PRIVATE mode blocks protected context from leaving the device.
3. HIGH and CRITICAL actions require owner approval.
4. Matching owner DENY rules fail closed.
5. Workspace terminal execution remains HIGH risk and policy evaluated.
6. GitHub write access remains proposal-oriented instead of unrestricted administration.
7. Mission dry-runs reject dangerous financial/system/safety-bypass intent and create no execution side effects.
8. Incident replay remains read-only with no external action attempt.
9. Chaos rehearsal remains isolated simulation with no production effect.
10. Workstation-agent CI keeps production activation, live money, withdrawals and transfers disabled.
11. Hosted CI cannot claim the owner's physical PC has passed WSL/Docker/GPU/end-to-end validation.

The scenario catalogue lives in `configs/stage26/safety-scenarios.json`.

## Determinism

`scripts/stage26/safety_certification.py` uses only the Python standard library. It performs no network calls, no subprocess execution, no model/tool invocation and no financial action.

For every scenario it records:

- PASS/FAIL;
- evidence file;
- SHA-256 of that evidence file;
- required and forbidden marker counts;
- any missing or unexpectedly present markers.

The report itself has no wall-clock timestamp, random identifier or machine-specific field. CI generates the evidence twice and requires byte-for-byte equality for both `safety-report.json` and `manifest.sha256`.

## Truth boundary

Every Stage 26 report must state:

```text
simulation_only = true
live_side_effects = false
external_action_attempted = false
physical_pc_status = NOT_TESTED
network_required = false
shell_execution_required = false
```

Changing any of those values makes certification fail.

Stage 26 does not earn the physical-PC validation points held back by Stages 24–25. Those require the owner's real hardware.

## Fail-closed behavior

Certification fails when:

- a required source marker disappears;
- a forbidden capability marker appears;
- the scenario file escapes the repository root;
- a scenario ID is duplicated;
- the schema/stage is changed unexpectedly;
- the suite stops declaring itself simulation-only;
- hosted CI attempts to claim physical-PC validation.

The harness never rewrites production code to make a scenario pass. A failing scenario requires review of the code change or an explicit, reviewed evolution of the safety contract.

## CI evidence

`.github/workflows/stage26-safety-certification.yml` runs on the canonical development branch and on pull requests toward `main`.

It:

1. compiles the verifier;
2. runs the fail-closed unit tests;
3. generates certification evidence twice;
4. compares both outputs byte-for-byte;
5. reasserts the truth boundary;
6. uploads the first deterministic evidence set for review.

## Development-line rule

After the Stage 24/25 reconciliation, new Syntra/Aetheris development continues on:

`feature/syntra-aetheris-foundation-v2`

`main` remains the stable release branch. The old Stage 24, Stage 25 and integration branches are retained only as historical branch references until they are explicitly cleaned up; no new feature work should be based on them.

## Physical-PC boundary

The following remain pending until the actual owner PC exists:

- WSL2 validation;
- Docker Desktop daemon validation;
- GPU/driver/VRAM validation;
- local-model latency and stability measurements;
- workstation-agent owner-PC activation;
- complete local-stack end-to-end validation;
- local thermals and sustained-load evidence.

Stage 26 intentionally makes none of those claims.
