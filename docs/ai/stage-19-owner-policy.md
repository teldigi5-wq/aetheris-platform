# Stage 19 Owner Policy

Stage 19 is an owner-controlled activation rehearsal layer. Its purpose is to prove the complete approval, canary-observation, revoke and rollback decision path before any real workstation activation exists.

## AUTO-SAFE

The following may happen without owner approval because they are read-only or deterministic evidence operations:

- read Stage 18 readiness and evidence-bundle state
- compute deterministic proposal/readiness hashes
- list Stage 19 proposals, observations and audit events
- assess canary SLO evidence
- generate read-only console summaries
- write audit events for actions already requested through governed Stage 19 endpoints

## OWNER APPROVAL REQUIRED

Starting the activation path requires an explicit, expiring owner approval. The approval must:

- identify `OWNER`
- be attached to one exact proposal
- expire within 1–30 minutes
- be rechecked before canary start
- remain bound to the exact Stage 18 evidence bundle, target, adapter, package and certificate

Approval does not confer production activation authority.

## CANARY LIMITS

Stage 19 canary capability grants are restricted to:

- PROCESS_READ
- PC_TELEMETRY
- OLLAMA
- VAD
- STT
- TTS
- PRIVATE_TRANSPORT

They must also be present in the signed Stage 18 bootstrap manifest.

The Stage 19 canary does not grant APP_LAUNCH, FILE_OPEN or DPAPI usage even when those capabilities exist in the Stage 18 manifest.

## AUTOMATIC SAFETY RESPONSE

A failed canary observation automatically enters `ROLLBACK_REHEARSED_SIMULATION` and records the decision in the audit ledger.

This is a rehearsal only. Repository/CI mode must always preserve:

- `simulationOnly=true`
- `productionActivationAllowed=false`
- `targetMutated=false`
- `externalActionAttempted=false`

## DISABLED / OUT OF SCOPE

Stage 19 must not provide or imply:

- automatic production activation
- unrestricted shell execution
- command prompt / PowerShell pass-through
- administrator or UAC bypass
- disabling endpoint/security protections
- credential extraction or export
- privilege self-promotion by a model or agent
- persistent physical target capability expansion without a new signed manifest and owner review
- live-money trading authority
- withdrawal or transfer authority

## STOP / REVOKE PRIORITY

Owner STOP, revoke and rollback controls outrank any canary progression. A revoked or rollback-rehearsed proposal cannot be considered active or validated.

## EVIDENCE FRESHNESS

Required Stage 18 target evidence must be fresh enough for Stage 19 review. Stage 19 uses a 30-minute maximum age for the target-measured evidence set during proposal, approval/start revalidation and attestation refresh.

If exact Stage 18 bundle correlation changes, Stage 19 fails closed and requires a new proposal.

## PHYSICAL TARGET RULE

Until the owner has the actual target PC and it produces verifiable target-measured evidence, all Stage 19 activation results are lab/simulation results only.

`CANARY_VALIDATED_SIMULATION_ONLY` means the state machine and evidence rules passed. It does not mean the workstation is activated.

## PRODUCTION ACTIVATION

Production activation remains a future, separately designed owner-controlled operation. Stage 19 intentionally has no state or endpoint that sets `productionActivationAllowed=true`.
