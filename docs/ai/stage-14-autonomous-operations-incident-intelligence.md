# Stage 14 — Autonomous Operations & Incident Intelligence

Stage 14 adds an evidence-first operations layer above the Stage 13 deployment/evidence fabric. It monitors governed health evidence, correlates incidents, analyzes impact and prepares remediation/escalation decisions without silently mutating external systems.

## Implemented capabilities

### Operational signal journal

`Stage14OperationalIntelligenceService` stores durable operational signals with:

- source type/id;
- signal type and severity;
- SHA-256 correlation fingerprint;
- optional source-measured SHA-256 attestation;
- observed timestamp; and
- linked incident ID when the signal is WARN or CRITICAL.

Source-measured signals require SHA-256 attestation evidence. Derived internal signals are explicitly marked as derived rather than provider/target measured.

### Incident correlation

WARN/CRITICAL signals are correlated by source identity plus SHA-256 fingerprint. An active incident is reused rather than opening duplicate incidents for the same continuing failure. Severity may escalate as stronger evidence arrives.

Incident states are durable: `OPEN`, `ACKNOWLEDGED`, `MITIGATION_PROPOSED`, and `RESOLVED`.

### Provider SLO monitoring

`Stage14SloMonitorService` consumes Stage 13 provider-health windows. A healthy window remains observational. A blocked window creates an evidence-backed incident candidate. An unavailable latest provider sample escalates the candidate to CRITICAL.

The monitor does **not** disable the provider.

### Containment proposals

`Stage14ContainmentService` evaluates narrowly defined containment actions:

- `PROVIDER_DISABLE`
- `TASK_PAUSE`
- `REMOTE_REVOKE`
- `TRADING_STOP`

High-impact containment requires a CRITICAL incident and exact task-bound owner approval using `STAGE14_CONTAINMENT`. A successful evaluation returns `AUTHORIZED_NOT_EXECUTED`; Stage 14 itself performs no external remediation.

### Governed self-healing proposals

Durable self-healing proposals support only bounded actions:

- `RESTART_SERVICE`
- `PAUSE_PROVIDER`
- `REVOKE_REMOTE_SESSION`
- `ROLLBACK_RELEASE`
- `PAUSE_TASK`
- `STOP_TRADING`

`ARBITRARY_SHELL`, `ADMIN_BYPASS`, `LIVE_ORDER`, `WITHDRAWAL`, and `TRANSFER` are rejected. Owner approval uses `STAGE14_SELF_HEAL`. Approval changes the proposal to `APPROVED_NOT_EXECUTED`; it does not execute the action.

### Change-impact graph

`Stage14ChangeImpactService` performs deterministic dependency analysis:

1. validate component/dependency identifiers;
2. detect dependency cycles;
3. traverse reverse dependencies from changed components;
4. identify affected critical components; and
5. compute a deterministic risk score/level.

Dependency cycles block deployment rehearsal.

### Digital-twin deployment rehearsal

`Stage14DeploymentRehearsalService` combines change impact, rollback availability, dependency-evidence completeness and a SHA-256 evidence reference. It returns `REHEARSAL_PASS` or `REHEARSAL_BLOCKED` and is always marked simulation-only. It cannot promote a production release.

### Escalation readiness

Incident severity maps deterministically to escalation level:

- CRITICAL → `IMMEDIATE_OWNER`
- WARN → `OWNER_ATTENTION`
- INFO → `AUDIT_ONLY`

Notification-provider readiness reuses Stage 12 provider gates. A successful evaluation is `ESCALATION_READY_NOT_SENT`; Stage 14 does not send the notification.

### Post-incident intelligence

Structured reports include incident state, evidence timeline, SHA-256 references and remediation proposal states. Root cause remains `UNDETERMINED_FROM_AVAILABLE_EVIDENCE` unless a future evidence-producing process establishes it. The system does not invent a causal explanation from correlation alone.

## API surface

Stage 14 APIs live under `/api/orchestrator/stage14` and expose signal/incident inspection, SLO assessment, containment evaluation, self-healing proposal state, change-impact analysis, rehearsal, escalation evaluation and post-incident reporting.

The `/stage14.html` console is intentionally read-only.

## Validation

The Stage 14 integration suite verifies:

- failed provider SLO windows produce incident candidates without disabling providers;
- duplicate fingerprints correlate into one active incident;
- CRITICAL evidence escalates incident severity;
- containment requires exact owner approval and remains not executed;
- forbidden self-healing actions are rejected;
- approved self-healing remains not executed;
- dependency impact is deterministic;
- digital-twin rehearsal is simulation-only;
- escalation does not send notifications; and
- reports do not invent root cause.

## External boundaries

Repository/CI validation does not mean the owner workstation or external providers are already remediated, restarted, revoked, rolled back or contacted. Real remediation remains a later target/provider operation requiring the corresponding owner approval, capability, transport and evidence gates.

Stage 14 does not widen any live-money, shell, administrator or remote-access privilege boundary from earlier stages.
