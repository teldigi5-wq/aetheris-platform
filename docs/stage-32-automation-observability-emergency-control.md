# Stage 32 — Automation, observability and emergency control

Status: **implemented; canonical merge is CI-gated**.

Stage 32 extends the existing orchestrator scheduler, notification, remote-companion, workflow, runtime and proactive subsystems. It does not create a second hidden orchestrator or an unrestricted remote shell.

## Implemented capabilities

### Agent Workflow Composer

`WorkflowComposer` validates workflow dependencies, rejects unknown dependencies and cycles, and returns deterministic topological execution order.

### Advanced Scheduler

`AdvancedScheduler` consumes Resource Budget, Retry Budget, user-load, energy-saver, hard-deadline and maximum-runtime inputs. It defers non-urgent work under user/energy pressure, blocks hard resource/retry violations, honors hard deadlines over soft energy deferral, and never lets deadlines outrank emergency control.

Runtime checkpoints re-check emergency mode and maximum runtime instead of assuming a decision remains valid forever.

### Event Trigger Engine and Watcher Abstraction

`WatcherAbstraction` normalizes WEBHOOK, FILE, SYSTEM, CI, MARKET and REVIEW sources into one bounded `AutomationEvent` contract. `EventTriggerEngine` rejects duplicate/debounced fingerprints deterministically.

### Notification Hub and Focus Mode

`NotificationHub` produces delivery decisions for desktop, sound and a configurable external-channel abstraction. Quiet hours and Focus Mode suppress ordinary notifications while CRITICAL notifications bypass those soft policies.

`FocusModeService` only changes notification/automation policy and exits immediately without mutating unrelated runtime state.

### Daily and End-of-Day Briefings

`BriefingGenerator` includes completed work, costs, risks, pending approvals and evidence references in both daily and end-of-day summaries.

### Automation Timeline and Audit Explorer

`AutomationTimeline` records what ran, when, subsystem/action class, actor, outcome, risk, evidence, cost and reason. `AuditExplorer` filters those records by time, subsystem, action class, actor, outcome, minimum risk and evidence reference.

### Remote Control Plane Guard

`RemoteControlGuard` requires an owner-authenticated short-lived session, scoped capability, fresh nonce and valid time window. Used nonces are rejected as replay attempts. During PAUSE/TAKE CONTROL/STOP, ordinary remote actions are rejected and only bounded emergency/read-status capabilities remain eligible.

This is an authorization guard only. Stage 32 does **not** add an unrestricted shell, arbitrary command tunnel or hidden remote executor.

### Artifact Manager

`ArtifactManager` requires artifact ID, location, producer, content hash, evidence references, creation time and retention deadline, and rejects duplicate artifact IDs.

### Emergency Stop / Pause / Take Control

`EmergencyControlService` uses deterministic precedence:

`STOP > TAKE_CONTROL > PAUSE > NORMAL`

A lower-priority request cannot silently weaken a stronger emergency state. Returning to NORMAL requires explicit owner confirmation.

### Single-orchestrator invariant

`OrchestratorBoundary` keeps `orchestrator-service` as the canonical runtime and explicitly reports that no hidden secondary orchestrator is enabled.

## Existing-system integration boundary

The repository already contains persistent scheduler tickets/worker heartbeats, notification delivery, remote companion sessions, workflow/runtime primitives and proactive services. Stage 32 adds deterministic control and evaluation primitives around those systems rather than replacing them.

No new HTTP endpoint, database table, Maven/npm/Python dependency, privileged shell, live-money path or production-activation authority is introduced.

## Validation

`.github/workflows/stage32-automation-control.yml` runs `Stage32FoundationTest` twice using pinned GitHub Actions and Java 21.0.12 to detect regressions and hidden state coupling.

The canonical PR gate also includes:

- full Build and orchestrator regression tests;
- unchanged Stage 23 compatibility-contract freeze;
- Stage 30 reasoning regression;
- Stage 31 digital-twin regression;
- Stage 32 automation/emergency-control regression;
- dependency lockdown and two-pass reproducible-build verification;
- CodeQL for Java and JavaScript/TypeScript.

## Physical-machine truth boundary

Hosted CI can validate deterministic repository behavior only. It does not prove browser/phone control on the owner's future machine, desktop notifications, sound, external-channel delivery, WSL/Docker/GPU behavior, thermals or real host automation. Those remain `BLOCKED_PENDING_HARDWARE` until real evidence exists.

## Next stage

Stage 33 — **Governance, approvals and cross-system policy** — should unify owner policy, approval semantics, cross-system authority and governance evidence around the Stage 30–32 reasoning/twin/automation control planes.
