# Stage 14 — Owner Policy

Stage 14 may autonomously observe, correlate, simulate and propose. It must not silently perform consequential remediation.

## Automatic / read-only

The system may automatically:

- record validated operational signals;
- correlate WARN/CRITICAL evidence into incidents;
- raise incident severity when stronger evidence arrives;
- assess Stage 13 provider-health windows;
- compute dependency/change impact;
- run digital-twin deployment rehearsals;
- evaluate notification escalation readiness;
- generate draft/final evidence reports; and
- surface read-only incident/proposal state in `/stage14.html`.

These actions must not create external side effects.

## Owner approval required

Owner approval is required before any real-world containment or remediation, including:

- disabling a provider;
- pausing a live task for incident containment;
- revoking a real remote session/device;
- invoking a trading stop on a connected execution environment;
- restarting a real service;
- rolling back a real release;
- opening/sending an escalation notification; or
- promoting any canary/recovery action onto a target environment.

Containment approvals use exact task-bound action `STAGE14_CONTAINMENT`.
Self-healing approvals use exact task-bound action `STAGE14_SELF_HEAL`.

Approval alone is not execution. In Stage 14, approved remediation remains `*_NOT_EXECUTED`.

## Prohibited

Stage 14 must not:

- expose or introduce arbitrary shell execution;
- bypass Windows UAC, administrator controls or security software;
- let an LLM/model decide its own privileges;
- treat configuration, correlation or simulation as target/provider proof;
- fabricate provider/target attestations;
- infer root cause from correlation alone;
- execute remediation solely because a confidence score is high;
- create live-money order authority;
- withdraw or transfer funds;
- broaden an exchange integration beyond testnet/sandbox boundaries; or
- represent CI success as proof that physical remediation occurred.

## Containment policy

Permitted Stage 14 containment *proposals* are limited to:

- `PROVIDER_DISABLE`
- `TASK_PAUSE`
- `REMOTE_REVOKE`
- `TRADING_STOP`

Provider disable, remote revoke and trading stop require a CRITICAL incident plus exact owner approval. Stage 14 evaluates authorization but does not perform the external action.

## Self-healing policy

Permitted proposal actions are limited to:

- `RESTART_SERVICE`
- `PAUSE_PROVIDER`
- `REVOKE_REMOTE_SESSION`
- `ROLLBACK_RELEASE`
- `PAUSE_TASK`
- `STOP_TRADING`

The following are explicitly rejected:

- `ARBITRARY_SHELL`
- `ADMIN_BYPASS`
- `LIVE_ORDER`
- `WITHDRAWAL`
- `TRANSFER`

Future execution adapters must preserve this allowlist or pass a new owner/security review before widening it.

## Incident evidence policy

Source-measured evidence requires a SHA-256 attestation reference. Internal derived signals must be marked derived. Duplicate evidence is correlated by source + fingerprint; a correlation is not proof of causation.

Incident state may advance automatically to `MITIGATION_PROPOSED` when a bounded recovery proposal is stored, but cannot be marked resolved by a model merely because a proposed action sounds plausible.

## Digital-twin policy

A rehearsal is a simulation. It may calculate affected components, risk, rollback requirements and blockers. It must not:

- deploy artifacts;
- alter provider settings;
- mutate target workstations;
- open tunnels;
- restart services; or
- promote releases.

A `REHEARSAL_PASS` therefore means only that the deterministic simulation gates passed.

## Escalation policy

Stage 14 may choose an escalation level and verify whether a Stage 12 notification provider is eligible. It must not send the notification automatically in this stage. `ESCALATION_READY_NOT_SENT` is the strongest successful escalation state.

## Root-cause policy

Post-incident reports must distinguish observation, correlation and verified cause. Unless explicit evidence establishes the cause, the report must retain:

`UNDETERMINED_FROM_AVAILABLE_EVIDENCE`

No model-generated narrative may silently overwrite that evidence state.

## Emergency precedence

Existing deterministic `STOP ALL`, `PAUSE`, and `TAKE CONTROL` controls continue to outrank monitoring, planning, model inference, notification escalation, deployment rehearsal and remediation proposals.

## Financial boundary

Stage 14 may propose `STOP_TRADING`; it does not gain order authority. The deterministic Risk Officer remains authoritative. Live orders, withdrawals and transfers remain disabled/outside the AI capability.
