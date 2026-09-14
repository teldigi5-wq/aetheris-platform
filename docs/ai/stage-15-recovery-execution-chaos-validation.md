# Stage 15 — Recovery Execution & Chaos Validation Fabric

Stage 15 extends Stage 14 incident intelligence with a governed recovery fabric. It defines how Syntra/Aetheris may move from an evidence-backed incident to a bounded recovery plan while keeping real target/provider mutation unavailable until external evidence and a compatible adapter exist.

## Core objective

The recovery lifecycle is:

`DETECT → UNDERSTAND → SIMULATE → CHECK SLO/ERROR BUDGET → OWNER APPROVE → TARGET EVIDENCE → VERIFY → ROLLBACK IF REQUIRED → REPLAY/REPORT`

Repository/CI success proves the policy/state machine, not a physical recovery on the owner's future workstation.

## Persistent service catalog

`Stage15ServiceCatalogService` stores governed recovery targets with:

- service id/name/type and criticality;
- deterministic SLO target in basis points;
- monthly error-budget metadata;
- dependency ids;
- per-service bounded-action allowlist;
- rollback requirement;
- persisted maintenance-window state.

Allowed bounded actions are:

- `RESTART_SERVICE`
- `PAUSE_PROVIDER`
- `REVOKE_REMOTE_SESSION`
- `ROLLBACK_RELEASE`
- `PAUSE_TASK`
- `STOP_TRADING`
- `FAILOVER_READ_ONLY_PROVIDER`
- `CLEAR_BOUNDED_CACHE`

The catalog rejects `ARBITRARY_SHELL`, `ADMIN_BYPASS`, `LIVE_ORDER`, `WITHDRAWAL`, `TRANSFER`, and any unknown action.

## SLO and error-budget evidence

`Stage15ReliabilityService` accepts only source-measured SHA-256-attested sample windows. It evaluates the latest 30 days and returns one of:

- `INSUFFICIENT_EVIDENCE`
- `MAINTENANCE_ACTIVE`
- `HEALTHY`
- `AT_RISK`
- `ERROR_BUDGET_EXHAUSTED`

The calculation is deterministic from measured total/failed samples and the catalog SLO target. Missing evidence is never converted into a healthy claim. An exhausted error budget blocks automated execution readiness.

## Isolated chaos rehearsal

`Stage15ChaosRehearsalService` supports only simulation faults:

- `DEPENDENCY_UNAVAILABLE`
- `HIGH_LATENCY`
- `SERVICE_CRASH_SIMULATION`
- `PROVIDER_DEGRADED`
- `DISK_PRESSURE_SIMULATION`

It reuses the Stage 14 dependency/change-impact graph to calculate downstream blast radius and critical affected components. A dependency cycle blocks the rehearsal. Production mutation is always false.

## Recovery plans and adapter boundary

`Stage15RecoveryService` creates durable recovery plans bound to:

- Stage 14 incident;
- Aetheris task;
- service-catalog entry;
- bounded action;
- bounded rollback action;
- deterministic verification policy;
- SHA-256 plan evidence.

Authorization requires an approved action exactly equal to `STAGE15_RECOVERY_EXECUTE` and the same task id as the recovery plan.

The Stage 15 adapter contract is deliberately implemented as an **evidence-only adapter**. It reports that a target execution adapter is not connected. Therefore approval produces `AUTHORIZED_PENDING_TARGET_EVIDENCE`, not an OS/provider mutation.

This is intentional because the owner's target PC and production/provider control adapters have not yet been physically validated.

## Target execution evidence and verification

Stage 15 can record externally observed target execution evidence only when it is marked target-measured and SHA-256 attested. Recording such evidence does not claim Aetheris executed the action.

Post-action verification supports:

- `HEALTHY_ATTESTATION`
- `SLO_RECOVERY`

A failed measured verification becomes `VERIFY_FAILED_ROLLBACK_REQUIRED`. A healthy verified state becomes `VERIFIED_RECOVERED`. Stage 15 does not silently execute the rollback; rollback remains a bounded governed action.

## Incident replay

`Stage15IncidentReplayService` reconstructs a read-only timeline from Stage 14 operational signals and Stage 15 recovery-plan states. Replay never re-executes tools or actions.

## Escalation delivery receipts

`Stage15DeliveryReceiptService` stores externally measured notification delivery evidence. It requires provider message id, provider-measured status and SHA-256 attestation. It records `DELIVERED`, `FAILED`, or `ACKNOWLEDGED` evidence; it does not send notifications itself.

## Reliability score

`Stage15ReliabilityScoreService` produces an explainable 0–100 score using:

- SLO/error-budget state;
- measured evidence depth;
- rollback coverage in recovery plans;
- critical operational-signal history.

The score returns component values and evidence gaps so it cannot hide missing proof behind a single number.

## API surface

Stage 15 endpoints live under `/api/orchestrator/stage15` and cover:

- service catalog and maintenance windows;
- reliability evidence/assessment/score;
- chaos rehearsal;
- recovery-plan creation, owner authorization, readiness, target evidence and verification;
- incident replay;
- delivery receipts;
- read-only overview.

The `/stage15.html` console is observational. It intentionally contains no execution button.

## Current external-validation boundary

Still pending until the owner has the target environment and deliberately configures it:

- physical Windows installation;
- target recovery adapter;
- owner-profile DPAPI validation;
- real service restart/provider disable/session revoke/release rollback;
- real production maintenance windows;
- real notification delivery receipts;
- physical chaos/fault injection;
- post-recovery hardware/application measurements.

No repository, CI, model or agent result may self-certify these external facts.

## Financial and privilege boundary

Stage 15 preserves all previous hard controls:

- no unrestricted autonomous shell;
- no UAC/admin/security bypass;
- no live-order authority;
- no withdrawals/transfers;
- deterministic Risk Officer remains authoritative;
- `STOP ALL`, `PAUSE`, and `TAKE CONTROL` remain higher priority than model/agent/background work.
