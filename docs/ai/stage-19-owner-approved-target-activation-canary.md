# Stage 19 — Owner-Approved Target Activation & Canary Lab

Stage 19 converts Stage 18 readiness evidence into a tightly bounded activation proposal and canary state machine. In repository/CI mode it remains simulation-only: no physical workstation is activated, no external action is attempted and no production capability is granted.

## Goals

1. Require a fresh, exact Stage 18 evidence bundle before activation can even be proposed.
2. Bind each proposal to the exact target, adapter, bootstrap manifest, host-agent package hash and device-certificate hash.
3. Require explicit OWNER approval with a short expiry window.
4. Revalidate Stage 18 readiness and the exact evidence bundle again immediately before starting a canary.
5. Restrict canary authority to a narrow subset of capabilities already signed into the Stage 18 manifest.
6. Observe canary health through deterministic SLO windows.
7. Rehearse rollback automatically when a canary observation fails.
8. Require multiple healthy windows before a canary can become simulation-validated.
9. Refresh target attestation without granting activation authority.
10. Persist a SHA-256 activation audit ledger for every important state change.

## Activation chain

The Stage 19 chain is:

`Stage 18 READY_FOR_OWNER_ACTIVATION_REVIEW`

→ exact current `EvidenceBundle.bundleSha256`

→ `AWAITING_OWNER_APPROVAL`

→ explicit `OWNER` approval valid for 1–30 minutes

→ exact Stage 18 revalidation

→ `CANARY_ACTIVE_SIMULATION`

→ at least three passing observations totaling at least 300 seconds

→ `CANARY_VALIDATED_SIMULATION_ONLY`

No Stage 19 state sets `productionActivationAllowed=true`.

## Exact binding

An activation proposal stores and rechecks:

- target ID
- Stage 16 adapter ID
- Stage 18 bootstrap-manifest SHA-256
- Stage 18 evidence-bundle SHA-256
- host-agent package SHA-256
- device-certificate SHA-256
- requested narrow canary capabilities

The proposal SHA-256 is deterministic over those values. If the Stage 18 manifest, bundle, package or certificate changes after proposal creation, approval/canary start fails closed.

## Freshness

Stage 19 does not accept an old 100-point Stage 18 snapshot indefinitely. Required Stage 18 target-measured evidence must still be passing and no older than 30 minutes when the proposal is created, approved, canary-started or attestation-refreshed.

The fresh evidence set includes:

- device identity
- package integrity
- OS vault / DPAPI binding
- RAM/VRAM resource benchmark
- speech/VAD benchmark
- local-model benchmark
- private transport
- every provider binding declared by the signed Stage 18 manifest

## Owner approval

`POST /api/orchestrator/stage19/proposals/{id}/approve`

requires `approvedBy=OWNER` and an approval lifetime from 1 to 30 minutes. Approval is not activation. Stage 19 revalidates exact target readiness again before canary start.

## Narrow canary capabilities

The Stage 19 canary allowlist is intentionally narrower than the Stage 18 bootstrap capability catalog:

- `PROCESS_READ`
- `PC_TELEMETRY`
- `OLLAMA`
- `VAD`
- `STT`
- `TTS`
- `PRIVATE_TRANSPORT`

Every requested capability must also be present in the signed Stage 18 bootstrap manifest.

`APP_LAUNCH`, `FILE_OPEN` and `DPAPI` may exist in Stage 18 but are deliberately excluded from the Stage 19 canary grant.

The following authorities remain outside the activation path entirely:

- arbitrary shell
- admin/UAC/security bypass
- credential export
- live orders
- withdrawals
- transfers

## Canary SLO observations

Each observation records:

- window duration: 60–3600 seconds
- availability percentage
- error-rate percentage
- p95 latency
- crash count
- source
- SHA-256 attestation
- whether the evidence was measured on a real target

A passing Stage 19 SLO window requires:

- availability >= 99.0%
- error rate <= 1.0%
- p95 latency <= 2000 ms
- zero crashes

Passing CI observations are labeled `PASS_SIMULATED`; target-reported observations can be labeled `PASS_TARGET_REPORTED`, but neither label grants production activation.

## Automatic rollback rehearsal

Any failed canary observation immediately transitions the proposal to:

`ROLLBACK_REHEARSED_SIMULATION`

and writes an `AUTO_ROLLBACK_REHEARSED` audit event. This is a deterministic simulation of the rollback decision. It never claims a Windows service, provider or release was actually changed.

Manual revoke and rollback-rehearsal endpoints also exist for owner/operator testing.

## Attestation refresh

Stage 19 can refresh an attestation only after revalidating the exact current Stage 18 target/bundle relationship. The refresh SHA-256 binds:

- proposal SHA-256
- evidence-bundle SHA-256
- manifest SHA-256
- package SHA-256
- certificate SHA-256

The result is `ATTESTATION_REFRESH_VERIFIED_SIMULATION_ONLY` and carries no production activation authority.

## Audit ledger

Every important transition is stored in `stage19_activation_audit` with:

- proposal ID
- target ID
- event type
- resulting state
- SHA-256 of event detail
- `simulationOnly=true`
- `targetMutated=false`
- `externalActionAttempted=false`

Raw secrets are not required or stored in this ledger.

## API summary

- `POST /api/orchestrator/stage19/proposals`
- `GET /api/orchestrator/stage19/proposals`
- `GET /api/orchestrator/stage19/proposals/{id}`
- `POST /api/orchestrator/stage19/proposals/{id}/approve`
- `POST /api/orchestrator/stage19/proposals/{id}/canary/start`
- `POST /api/orchestrator/stage19/proposals/{id}/canary/observe`
- `GET /api/orchestrator/stage19/proposals/{id}/canary/assessment`
- `POST /api/orchestrator/stage19/proposals/{id}/canary/validate`
- `POST /api/orchestrator/stage19/proposals/{id}/revoke`
- `POST /api/orchestrator/stage19/proposals/{id}/rollback/rehearse`
- `POST /api/orchestrator/stage19/proposals/{id}/attestation/refresh`
- `GET /api/orchestrator/stage19/observations`
- `GET /api/orchestrator/stage19/audit`
- `GET /api/orchestrator/stage19/overview`

## Console

`/stage19.html` is authenticated and read-only. It displays proposal states, observation evidence and the activation audit ledger. It contains no activation, approval, rollback, revoke or execute button.

## Evidence-honesty boundary

A green Stage 19 CI run proves that the policy/state-machine implementation works in repository tests. It does not prove:

- the owner's future PC was activated
- a real Windows command ran
- a real mTLS tunnel is online
- real target SLOs passed
- a production certificate is installed
- production adapter activation is approved

Physical activation must remain pending until the actual target PC exists and produces fresh target-measured evidence.
