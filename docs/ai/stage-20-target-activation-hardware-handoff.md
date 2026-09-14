# Stage 20 — Target Activation Authorization & Hardware Handoff Protocol

Stage 20 defines the final repository-side control protocol before a physical Windows workstation can be handed limited activation authority. It extends Stage 19 and deliberately remains simulation/contract-only while the owner does not yet have the target PC.

## Goals

1. Bind activation to a fresh, validated Stage 19 canary and exact Stage 18 evidence bundle.
2. Require an owner-signed Ed25519 authorization artifact.
3. Separate the owner authorization signer from the device-attestation signer.
4. Bind authorization to exact target, adapter, manifest, package, certificate and attestation hashes.
5. Restrict activation to a signed maintenance window and short authorization lifetime.
6. Use one-time hardware challenge/response before a lease can start or renew.
7. Grant only bounded, non-expanding activation leases.
8. Make emergency stop asymmetric: STOP is immediate, clearing requires fresh owner signature.
9. Verify device-signed target receipts and revoke automatically on continuity drift.
10. Produce a deterministic post-activation evidence bundle without claiming a physical activation occurred.

## Trust roots

Stage 20 deliberately uses two distinct Ed25519 trust identities already stored as Stage 16 public signers:

- **Owner signer** — authorizes the exact handoff artifact and clears an emergency-stop interlock.
- **Device signer** — proves possession of the enrolled device identity by signing short-lived challenge responses and target receipts.

The same signer ID cannot be used for both roles. Private keys are not stored in the repository or ordinary database model.

## Owner-signed authorization

`Stage20HardwareHandoffService.authorize` accepts an authorization only when:

- the referenced Stage 19 proposal is `CANARY_VALIDATED_SIMULATION_ONLY`;
- Stage 19 has a fresh attestation refresh no older than 30 minutes;
- Stage 18 still reports `READY_FOR_OWNER_ACTIVATION_REVIEW` with score 100;
- Stage 19 and Stage 18 still agree on target, adapter, manifest, package, certificate and evidence-bundle hashes;
- owner and device signer IDs are different trusted signers;
- requested capabilities are a non-empty subset of the Stage 19 canary grant;
- `maxLeaseMinutes` is 1–10 minutes;
- the authorization issue time is no older than five minutes and not materially future-dated;
- authorization expiry is no more than 30 minutes after issue;
- the maintenance window is positive and no longer than one hour;
- authorization expiry does not exceed the maintenance-window end;
- the submitted SHA-256 matches the canonical authorization payload;
- the owner Ed25519 signature verifies over that exact canonical payload.

The resulting state is `AUTHORIZED_SIMULATION_ONLY`. It never grants production activation.

## Canonical authorization binding

The owner signature covers:

- Stage 19 proposal ID and SHA-256;
- target ID;
- adapter ID;
- Stage 18 manifest SHA-256;
- Stage 18 evidence-bundle SHA-256;
- host-agent package SHA-256;
- device-certificate SHA-256;
- Stage 19 refreshed-attestation SHA-256;
- owner signer ID;
- device signer ID;
- bounded capability set;
- issue and expiry timestamps;
- maintenance-window start/end;
- maximum lease duration.

Changing any of these requires a new owner-signed artifact.

## Hardware challenge / response

`POST /api/orchestrator/stage20/authorizations/{authorizationId}/challenges` creates a cryptographically random 32-byte nonce.

Only the SHA-256 of the nonce is persisted. The raw nonce is returned once to the caller so a future workstation can sign the challenge locally. Challenges expire after two minutes.

The device attestation canonical payload binds:

- challenge ID;
- authorization ID;
- target ID;
- nonce SHA-256;
- adapter ID;
- package SHA-256;
- device-certificate SHA-256;
- observed timestamp.

The configured device signer must verify the response. A verified challenge can be consumed only once.

## Bounded activation lease

A lease can start only when:

- the owner authorization is active and unexpired;
- current time is inside the signed maintenance window;
- Stage 19/18 continuity still passes;
- emergency stop is not engaged;
- a fresh verified device challenge exists and has not been consumed;
- lease capabilities are a subset of the owner-signed authorization;
- requested duration does not exceed the signed maximum.

The lease expiry is deterministically capped by the earliest of:

1. requested lease end;
2. authorization expiry;
3. maintenance-window end.

Lease renewal requires a **new** verified one-time hardware challenge. A consumed challenge cannot be replayed.

Repository mode states are:

- `LEASE_ACTIVE_SIMULATION_ONLY`
- `LEASE_RENEWED_SIMULATION_ONLY`
- `LEASE_REVOKED_SIMULATION_ONLY`

## Emergency-stop interlock

STOP is intentionally asymmetric.

### Engage

Engaging the Stage 20 emergency stop:

- requires only a bounded non-secret reason;
- immediately sets `EMERGENCY_STOPPED_SIMULATION`;
- revokes all active Stage 20 leases;
- blocks new challenges and leases;
- emits a hashed audit event.

### Clear

Clearing STOP requires:

- explicit `OWNER` identity;
- a current, unexpired owner authorization;
- fresh Stage 19/18 continuity;
- a fresh timestamp;
- a new Ed25519 owner signature over the exact interlock-clear artifact.

Clearing STOP never resurrects previously revoked leases. New challenge/attestation evidence is required for another lease.

## Signed target receipts

A future device can submit a Stage 20 receipt only during an active lease. The device signature binds:

- lease ID;
- authorization ID;
- target ID;
- lease SHA-256;
- package SHA-256;
- device-certificate SHA-256;
- target-attestation SHA-256;
- result code;
- target-measured flag;
- observation time.

Allowed result codes are `SUCCESS`, `HEALTHY`, `FAILED`, and `REJECTED`.

Repository mode rejects any receipt that claims `targetMutated=true` or `externalActionAttempted=true`, because the current implementation cannot honestly verify physical execution.

### Drift response

If a valid device-signed receipt reports a package, certificate or target-attestation hash that no longer matches the current lease/authorization continuity:

1. authorization enters `REVOKED_ATTESTATION_DRIFT_SIMULATION`;
2. all active leases are revoked;
3. receipt becomes `REJECTED_ATTESTATION_DRIFT_SIMULATION`;
4. a hashed `ATTESTATION_DRIFT_AUTO_REVOKE` audit event is persisted.

This is fail-closed and does not depend on model judgement.

## Post-activation evidence bundle

`GET /api/orchestrator/stage20/authorizations/{authorizationId}/evidence-bundle` hashes together:

- owner authorization SHA-256;
- current Stage 18 bundle SHA-256;
- authorization state;
- hardware challenge evidence;
- lease and renewal evidence;
- signed target receipts.

Possible repository states include:

- `HANDOFF_EVIDENCE_INCOMPLETE`
- `POST_ACTIVATION_EVIDENCE_SIMULATION_ONLY`
- `HANDOFF_REVOKED_SIMULATION`

Every bundle reports:

- `productionActivationAllowed=false`
- `targetMutated=false`
- `externalActionAttempted=false`

## REST surface

Read/write control endpoints exist under `/api/orchestrator/stage20` for future governed clients:

- `POST /authorizations`
- `POST /authorizations/{id}/challenges`
- `POST /challenges/{id}/attest`
- `POST /authorizations/{id}/leases`
- `POST /leases/{id}/renew`
- `POST /authorizations/{id}/emergency-stop`
- `POST /authorizations/{id}/emergency-stop/clear`
- `POST /leases/{id}/receipts`

Evidence/read endpoints:

- `GET /authorizations`
- `GET /challenges`
- `GET /leases`
- `GET /receipts`
- `GET /authorizations/{id}/evidence-bundle`
- `GET /audit`
- `GET /overview`

The Stage 20 web console is intentionally **read-only** and exposes none of the write actions above.

## CI conformance

`Stage20IntegrationTest` proves:

1. owner and device trust roles remain separate;
2. a signature from the wrong signer is rejected;
3. capability widening is rejected;
4. a consumed hardware challenge cannot be replayed;
5. lease renewal requires fresh device evidence;
6. STOP revokes active leases;
7. STOP clear requires the owner signer;
8. signed attestation drift automatically revokes authorization and leases;
9. a valid simulation receipt produces a deterministic evidence bundle;
10. repository mode rejects any receipt claiming real target mutation.

## External validation still required

Stage 20 repository/CI success does **not** prove:

- a physical Windows device possesses the device private key;
- a TPM/hardware-backed key exists;
- the real host-agent package hash was measured on the target PC;
- the real certificate chain or private tunnel is active;
- a physical capability was actually executed;
- a real lease crossed the network to a workstation;
- production activation is safe.

Those claims require physical target evidence after the owner has the workstation.

## Non-negotiable exclusions

Stage 20 cannot grant or inherit authority for:

- arbitrary shell execution;
- admin/UAC/security bypass;
- credential export;
- live exchange orders;
- withdrawals;
- transfers.

`STOP ALL`, `PAUSE`, and `TAKE CONTROL` remain above model/provider/background work in the overall Aetheris authority hierarchy.
