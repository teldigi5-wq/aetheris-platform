# Stage 20 Owner Policy — Target Activation Authorization & Hardware Handoff

This policy defines what Syntra/Aetheris may and may not do during Stage 20. Stage 20 is the final pre-hardware handoff contract. The owner does not yet have the target PC, so all current activation and lease states remain simulation/contract evidence only.

## Authority order

1. Platform/legal/provider constraints
2. Owner emergency controls (`STOP ALL`, `PAUSE`, `TAKE CONTROL`)
3. Owner-signed Stage 20 activation authorization
4. Stage 20 hardware/device attestation and lease policy
5. Stage 19 canary validation
6. Stage 18 target readiness
7. Agent/model recommendations

A model, provider, agent or background worker cannot promote itself above any earlier layer.

## AUTO-SAFE

Aetheris may automatically:

- verify canonical SHA-256 bindings;
- verify trusted Ed25519 signatures;
- compare exact target/package/certificate/adapter hashes;
- reject stale or future-dated authorization evidence;
- enforce signed maintenance windows;
- issue cryptographically random one-time challenges;
- reject challenge replay;
- narrow lease capability sets;
- cap lease expiry to the signed authorization/window;
- hash non-secret audit evidence;
- detect attestation drift;
- revoke simulation leases on attestation drift;
- engage fail-closed status transitions after deterministic evidence failures;
- assemble deterministic post-activation evidence bundles.

These are deterministic policy/evidence operations, not physical target actions.

## OWNER-SIGNED

The following require cryptographic owner authorization:

- creation of a Stage 20 activation authorization artifact;
- clearing an engaged Stage 20 emergency-stop interlock.

The owner signature must cover the exact canonical artifact. Typed text such as `OWNER` is not a substitute for the Ed25519 signature where Stage 20 requires one.

## DEVICE-SIGNED

The device trust identity must sign:

- hardware challenge responses;
- target execution/health receipts.

The device signer must be a separate trusted key from the owner signer.

## STOP-FIRST

Emergency stop is deliberately easier to engage than to clear.

When STOP is engaged:

- active Stage 20 leases are revoked immediately;
- new challenges are blocked;
- new leases and renewals are blocked;
- no model can override the interlock.

Clearing STOP requires a fresh owner-signed clear artifact. Revoked leases stay revoked.

## BOUNDED LEASE POLICY

A Stage 20 lease:

- may include only capabilities already present in the signed Stage 20 authorization;
- may never widen Stage 19 canary authority;
- is limited by the owner-signed `maxLeaseMinutes`, with an absolute supported range of 1–10 minutes;
- cannot outlive the authorization expiry;
- cannot outlive the maintenance window;
- requires a fresh verified one-time device challenge to start;
- requires another fresh challenge to renew;
- can be revoked by STOP or deterministic attestation drift.

## EVIDENCE HONESTY

Repository/CI results must never be described as proof that:

- the owner's physical PC was activated;
- a TPM or hardware private key exists;
- a Windows process/app/file action occurred;
- a private tunnel is operational;
- a target was mutated;
- production activation is enabled.

Current Stage 20 states therefore keep:

- `simulationOnly=true`
- `productionActivationAllowed=false`
- `targetMutated=false`
- `externalActionAttempted=false`

A future target-reported receipt may be stored as `VERIFIED_TARGET_REPORTED_CONTRACT_ONLY`, but even that label is not production activation authority by itself.

## ATTESTATION DRIFT

A signed device receipt that proves package/certificate/attestation continuity changed must fail closed:

- revoke the authorization;
- revoke active leases;
- preserve the signed receipt and audit evidence;
- require a new readiness/authorization ceremony before any later activation attempt.

No model judgement is allowed to suppress this deterministic revocation.

## DISABLED / OUTSIDE AUTHORITY

Stage 20 cannot authorize:

- `ARBITRARY_SHELL`
- unrestricted command execution
- PowerShell/cmd shell delegation to an LLM
- `ADMIN_BYPASS`
- UAC bypass
- security-control disabling
- BIOS/firmware changes
- credential export
- signing-private-key export
- live exchange orders
- withdrawals
- asset transfers
- removal of the deterministic Risk Officer

## FINANCIAL BOUNDARY

The hardware handoff protocol has no financial authority. Trading remains governed by the separate deterministic trading boundaries. Stage 20 cannot convert paper/testnet readiness into live-money execution.

## REAL TARGET ACTIVATION REQUIREMENTS

Before any future physical activation can be considered, the owner must have the target PC and the system must collect fresh evidence for at least:

- host-agent package integrity on that machine;
- device/certificate enrollment;
- Windows-profile DPAPI behavior;
- real RAM/VRAM/local-model performance;
- real microphone/VAD/STT/TTS behavior;
- private transport/certificate chain;
- application-path allowlists;
- emergency-stop behavior on the real target;
- lease delivery and revocation behavior;
- rollback/recovery behavior.

The current Stage 20 implementation intentionally stops before granting production activation authority.

## Merge policy

PR #7 remains draft and unmerged. Before an eventual merge, protect `main` and require the validated CI checks so direct or force changes cannot bypass the evidence gates accumulated through these stages.
