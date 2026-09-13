# Stage 21 — Physical Target Pilot & Restricted Capability Activation

Stage 21 is the first milestone whose honest completion requires the owner's real Windows workstation. Repository work can prepare the pilot protocol and validate its fail-closed behavior, but CI cannot manufacture physical evidence.

## Repository-ready flow

1. A Stage 21 pilot plan binds one current Stage 20 authorization, exact target ID, adapter ID, package SHA-256 and device-certificate SHA-256.
2. Requested pilot capabilities must be a non-empty subset of the Stage 20 authorization and the restricted pilot allowlist.
3. Pilot plans always begin as `BLOCKED_PENDING_HARDWARE`.
4. Evidence can be stored as simulated or target-measured, but simulated evidence never contributes to physical readiness.
5. Target-measured `DEVICE_IDENTITY` and `PACKAGE_INTEGRITY` evidence must match the exact Stage 20 certificate/package hashes.
6. `STAGE20_LEASE_RECEIPT` must correlate to a successful/healthy Stage 20 target-measured receipt for the same authorization, package and certificate.
7. Physical readiness uses only fresh target-measured evidence from the last 24 hours.
8. Even 100% evidence produces only `READY_FOR_OWNER_RESTRICTED_PILOT_REVIEW`. Stage 21 repository code contains no production-activation transition.

## Restricted capability set

- `PROCESS_READ`
- `PC_TELEMETRY`
- `OLLAMA`
- `VAD`
- `STT`
- `TTS`
- `PRIVATE_TRANSPORT`

The pilot cannot widen Stage 20 authority. `APP_LAUNCH`, `FILE_OPEN`, `DPAPI` execution authority, arbitrary shell, admin/UAC bypass, credential export, live orders, withdrawals and transfers are not part of the Stage 21 pilot grant.

## Physical evidence checklist

The readiness score is based on twelve required evidence classes:

- `HOST_AGENT_INSTALL`
- `DEVICE_IDENTITY`
- `PACKAGE_INTEGRITY`
- `OS_VAULT`
- `RESOURCE_BENCHMARK`
- `LOCAL_MODEL_BENCHMARK`
- `SPEECH_BENCHMARK`
- `PRIVATE_TRANSPORT`
- `STAGE20_LEASE_RECEIPT`
- `STOP_DRILL`
- `REVOCATION_DRILL`
- `ROLLBACK_DRILL`

A passing record counts only when `measuredOnTarget=true`, the record is fresh, and any exact subject binding required by the evidence kind still matches.

## State model

- `BLOCKED_PENDING_HARDWARE` — zero acceptable physical evidence; expected while the owner does not have the PC.
- `HARDWARE_EVIDENCE_INCOMPLETE` — some fresh physical evidence exists, but one or more required gates are missing/failing/stale.
- `READY_FOR_OWNER_RESTRICTED_PILOT_REVIEW` — all twelve gates pass with fresh target-measured evidence. This is review eligibility, not activation.

The following remain false in repository-only Stage 21:

- `physicalPilotComplete`
- `ownerPilotActivationAllowed`
- `productionActivationAllowed`
- `targetMutated`
- `externalActionAttempted`

## When the target PC arrives

Use the real workstation to install the signed host-agent package, enroll the real device identity, validate owner-profile DPAPI/OS-vault behavior, capture RAM/VRAM/local-model/voice benchmarks, establish the private transport, create a fresh Stage 20 authorization/challenge/lease, collect a signed target receipt, and execute STOP/revocation/rollback drills. Only those real measurements should be submitted as target evidence.

CI fixtures may test the validation algorithm, including a synthetic `measuredOnTarget=true` fixture, but such fixtures are test data and must never be represented as evidence that the owner's physical workstation was actually validated.
