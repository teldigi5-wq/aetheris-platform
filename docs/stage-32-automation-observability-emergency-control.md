# Stage 32 — Automation, observability and emergency control

Status: **implementation candidate — validation required before roadmap completion**.

Stage 32 extends the existing orchestrator scheduler, notification, remote-companion, workflow, runtime and proactive subsystems. It does not create a second hidden orchestrator or an unrestricted remote shell.

## Implemented foundation

- Agent workflow composition with dependency and cycle validation.
- Advanced deterministic scheduling with Resource Budget, user-load, energy policy, hard-deadline and Retry Budget checks.
- Event Trigger Engine with normalized WEBHOOK, FILE, SYSTEM, CI, MARKET and REVIEW events plus dedup/debounce.
- Notification Hub routing with desktop, sound and configurable external-channel decisions; quiet hours, urgency and Focus Mode are respected.
- Daily Briefing and End-of-Day Summary generation that always exposes costs, risks, pending approvals and evidence references.
- Focus Mode that changes notification/automation policy and exits immediately without mutating unrelated state.
- Append-only in-process Automation Timeline and an Audit Explorer filter model for time, subsystem, action class, actor, outcome, risk and evidence.
- Remote Control Guard for short-lived owner-authenticated sessions, scoped capabilities, replay-protected nonces and emergency-control precedence. It authorizes control-plane actions only; it is not a remote command executor.
- Resource-aware scheduling that defers non-urgent work but allows hard deadlines to outrank energy deferral while never outranking STOP/PAUSE/TAKE CONTROL.
- Artifact Manager with provenance, hash, evidence-reference and retention metadata.
- Watcher Abstraction Layer that normalizes bounded source events before automation policy sees them.
- Emergency Control with deterministic `STOP > TAKE_CONTROL > PAUSE > NORMAL` precedence and explicit owner-confirmed release.
- Runtime checkpointing for emergency-state re-check and maximum-runtime enforcement.
- Explicit single-orchestrator boundary: canonical runtime remains `orchestrator-service`; no secondary orchestrator is enabled.

## Existing-system integration boundary

The repository already contains persistent scheduler tickets/worker heartbeats, notification delivery, remote companion sessions, workflow/runtime primitives and proactive services. Stage 32 adds policy/evaluation primitives around those systems rather than replacing them.

No new HTTP endpoint, database table, Maven/npm/Python dependency, privileged shell, live-money path or production-activation authority is introduced by this Stage 32 foundation.

## Validation target

`.github/workflows/stage32-automation-control.yml` runs `Stage32FoundationTest` twice using pinned GitHub Actions and Java 21.0.12. Before Stage 32 can be marked complete, the branch must also pass the canonical Build, compatibility contract, dependency lockdown/reproducibility, Stage 30/31 regressions and CodeQL checks.

## Physical-machine truth boundary

Hosted CI can validate deterministic repository behavior only. It does not prove browser/phone control on the owner's future machine, desktop notifications, sound, external-channel delivery, WSL/Docker/GPU behavior, thermals or real host automation. Those remain `BLOCKED_PENDING_HARDWARE` until real evidence exists.
