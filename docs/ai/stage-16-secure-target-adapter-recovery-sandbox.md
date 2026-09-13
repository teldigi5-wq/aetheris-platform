# Stage 16 — Secure Target Adapter & Recovery Sandbox

Stage 16 converts the Stage 15 evidence-only recovery boundary into a cryptographically governed adapter protocol while keeping repository/CI execution simulation-only. It is designed so the future owner workstation/provider adapters can be added later without weakening owner approval, evidence honesty, bounded capability, replay protection, rollback or emergency-stop guarantees.

## Goals

1. Admit only short-lived recovery commands bound to an exact Stage 15 recovery plan.
2. Verify commands with Ed25519 signatures without storing signing private keys in the repository.
3. Prevent replay with a durable one-time nonce journal.
4. Enforce exact adapter capability, action, service target and plan SHA-256 binding.
5. Preserve Stage 15 reliability/error-budget and maintenance-window gates.
6. Provide a deterministic sandbox for action, verification, rollback, timeout and adapter-loss rehearsal.
7. Give owner cancellation/STOP priority before sandbox execution.
8. Produce durable simulation receipts without claiming a physical target was mutated.

## Components

### `Stage16AdapterRegistryService`

Registers capability-scoped adapters. In Stage 16 repository mode only these adapter types are accepted:

- `SIMULATED_WINDOWS`
- `SIMULATED_PROVIDER`
- `SIMULATED_CONTROL`

Every registered adapter must declare:

- `simulationOnly=true`
- `attestationKind=CI_SIMULATED`
- a SHA-256 attestation reference
- a non-empty subset of the Stage 15 bounded recovery action allowlist

Real target types and forbidden capabilities are rejected.

### `Stage16CommandTrustService`

Stores trusted Ed25519 **public keys only**. Signing private keys are intentionally external to the repository/runtime data model.

Each signer has:

- stable `keyId`
- display name
- X.509-encoded Ed25519 public key in Base64
- public-key SHA-256 fingerprint
- enabled state

### Signed execution envelope

`Stage16SecureRecoveryService` admits an envelope only when all of the following hold:

- referenced Stage 15 recovery plan exists;
- plan has exact owner approval and is in `AUTHORIZED_PENDING_TARGET_EVIDENCE`;
- Stage 15 service exists;
- error budget is not exhausted;
- no maintenance window is active;
- adapter exists, is enabled and is simulation-only;
- requested action exactly equals the Stage 15 plan action;
- target exactly equals the Stage 15 service id;
- adapter capability contains the action;
- service catalog allows the action;
- action is not a forbidden authority;
- nonce is syntactically valid and has never been used;
- issue/expiry timestamps are valid;
- envelope lifetime is at most five minutes;
- plan SHA-256 exactly matches Stage 15;
- Ed25519 signature verifies against the trusted signer public key.

Canonical signed payload:

```text
planId|adapterId|action|target|nonce|issuedAtEpochMillis|expiresAtEpochMillis|planSha256
```

### Replay protection

Accepted nonces are stored durably in `stage16_execution_envelopes` with a uniqueness constraint. Reusing an accepted nonce is rejected even if the signature is otherwise valid.

### Emergency cancellation

Execution is intentionally two-phase:

```text
SIGNED REQUEST
  -> ADMIT
  -> ADMITTED
       |-> OWNER STOP / CANCEL -> CANCELLED
       `-> EXECUTE SANDBOX
```

Only `ADMITTED` envelopes may execute. Once cancelled, the envelope cannot enter the sandbox.

### Recovery sandbox

`Stage16RecoverySandboxService` provides deterministic failure scenarios:

- `NORMAL`
- `ACTION_FAILURE`
- `VERIFY_FAILURE`
- `ROLLBACK_FAILURE`
- `ADAPTER_DISCONNECT`
- `TIMEOUT`

The sandbox always returns:

- action attempt/success state;
- verification attempt/success state;
- rollback attempt/success state;
- deterministic SHA-256 receipt;
- `simulationOnly=true`;
- `targetMutated=false`;
- `externalActionAttempted=false`.

A verification failure automatically rehearses rollback. This is not a production rollback.

## Current states

Envelope states:

- `ADMITTED`
- `CANCELLED`
- `SIMULATION_COMPLETED`

Typical sandbox outcomes:

- `SIMULATED_RECOVERED`
- `SIMULATED_ACTION_FAILED`
- `SIMULATED_ADAPTER_DISCONNECTED`
- `SIMULATED_TIMEOUT`
- `SIMULATED_VERIFY_FAILED_ROLLED_BACK`
- `SIMULATED_VERIFY_FAILED_ROLLBACK_FAILED`

## API surface

Read/query:

- `GET /api/orchestrator/stage16/adapters`
- `GET /api/orchestrator/stage16/signers`
- `GET /api/orchestrator/stage16/envelopes`
- `GET /api/orchestrator/stage16/envelopes/{id}`

Governed setup/sandbox operations:

- `POST /api/orchestrator/stage16/adapters`
- `POST /api/orchestrator/stage16/signers`
- `POST /api/orchestrator/stage16/envelopes/admit`
- `POST /api/orchestrator/stage16/envelopes/{id}/cancel`
- `POST /api/orchestrator/stage16/envelopes/{id}/execute-sandbox`

The Stage 16 dashboard is read-only and intentionally exposes no execution control.

## Security boundaries

Stage 16 does not add:

- arbitrary shell execution;
- Windows admin/UAC bypass;
- security-control bypass;
- live exchange orders;
- withdrawals;
- transfers;
- unrestricted provider mutation;
- production chaos injection;
- target private-key storage.

The Stage 15 forbidden actions remain forbidden:

`ARBITRARY_SHELL`, `ADMIN_BYPASS`, `LIVE_ORDER`, `WITHDRAWAL`, `TRANSFER`.

## Validation

`Stage16IntegrationTest` verifies:

- real adapters are rejected in repository mode;
- forbidden capabilities are rejected;
- Ed25519 signed envelopes are admitted;
- tampered signatures fail;
- nonce replay fails;
- exact Stage 15 binding is enforced;
- error-budget exhaustion blocks admission;
- simulated verification failure triggers simulated rollback;
- target mutation stays false;
- external action attempts stay false;
- owner cancellation blocks execution.

## Future physical adapter activation

A later target-validation stage may introduce real Windows/provider adapters only after the owner has the target environment. That activation should require, at minimum:

1. real target identity and certificate chain;
2. owner-controlled signing key material outside Git;
3. mutual authenticated private transport;
4. replay-protected target-side command journal;
5. capability-scoped adapter executable;
6. real target attestation and executable hash evidence;
7. STOP/PAUSE revocation path;
8. post-action measured verification;
9. rollback conformance testing;
10. owner approval for enabling physical execution.

Until those conditions are met, Stage 16 remains a secure protocol and sandbox implementation, not a claim of physical recovery execution.
