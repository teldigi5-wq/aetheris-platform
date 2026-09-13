# Stage 16 Owner Policy — Secure Target Adapter & Recovery Sandbox

Stage 16 exists to make future recovery execution safer, not more autonomous. Owner authority, deterministic policy, bounded capability and evidence truth remain above model/agent intent.

## 1. Owner authority

The owner remains the final authority for high-impact recovery actions. A Stage 16 signed execution envelope does not replace the Stage 15 owner approval requirement; it adds a second cryptographic transport boundary after approval.

Required chain:

```text
incident evidence
-> Stage 15 recovery plan
-> exact task-bound owner approval
-> Stage 16 signed envelope
-> adapter/scope/replay/time validation
-> owner cancellation window
-> sandbox or future target adapter
-> measured verification
-> rollback when required
```

## 2. STOP / cancellation priority

`STOP ALL`, `PAUSE`, explicit cancellation and owner takeover outrank model/provider/background activity.

An `ADMITTED` Stage 16 envelope may be cancelled. A cancelled envelope cannot execute. Future physical adapters must independently honor cancellation/revocation even if the orchestrator process is unavailable.

## 3. Private keys

Do not commit or store owner signing private keys in source control, database fixtures, documentation, CI variables printed to logs or ordinary Aetheris payloads.

Stage 16 stores trusted public keys and fingerprints only. Future private keys should use owner-controlled OS/hardware-backed secret storage where practical.

## 4. Command lifetime and replay

Every execution envelope must:

- have a unique nonce;
- be short-lived;
- use a lifetime of at most five minutes;
- bind exact plan SHA, adapter, action and target;
- pass Ed25519 verification.

A previously admitted nonce is never reusable.

## 5. Capability scope

Adapters are deny-by-default. They may expose only explicit bounded actions already permitted by the Stage 15 service catalog.

Always forbidden:

- `ARBITRARY_SHELL`
- `ADMIN_BYPASS`
- `LIVE_ORDER`
- `WITHDRAWAL`
- `TRANSFER`

No model, agent, adapter or signed envelope may self-promote these permissions.

## 6. Repository/CI mode

Until real target validation exists, only simulation adapters are allowed:

- `SIMULATED_WINDOWS`
- `SIMULATED_PROVIDER`
- `SIMULATED_CONTROL`

They must be `simulationOnly=true` and use `CI_SIMULATED` attestation. CI success must not be described as physical workstation/provider execution.

## 7. Reliability gates

A valid signature is necessary but not sufficient. Stage 16 must also preserve deterministic operational gates.

Admission is blocked when:

- Stage 15 plan is not currently authorized;
- error budget is exhausted;
- maintenance is active;
- adapter capability is missing;
- service catalog does not permit the action;
- plan SHA does not match;
- envelope is expired, future-dated or too old;
- signature fails;
- nonce is reused.

## 8. Verification and rollback

Recovery success is never inferred merely because an action was attempted.

Future real execution must provide target-measured post-action evidence. Failed verification must transition into a rollback-required path when a rollback exists.

Stage 16 CI only rehearses this state machine. Sandbox rollback is not production rollback.

## 9. Financial authority

Stage 16 does not expand trading authority.

- market intelligence has no execution authority by itself;
- deterministic risk policy remains authoritative;
- live orders remain disabled;
- withdrawals and transfers remain outside AI capability;
- `STOP_TRADING` may be a bounded protective control but cannot create new positions.

## 10. Shell/admin authority

No unrestricted shell, privilege escalation, UAC bypass, security-control bypass or hidden persistence is introduced by Stage 16.

Future Windows recovery adapters should expose narrow typed operations such as a specific registered-service restart rather than a generic command shell.

## 11. Evidence honesty

The system must distinguish:

- repository implementation;
- CI simulation;
- provider-reported evidence;
- target-measured evidence;
- actual physical execution.

Aetheris must not claim a real target action occurred unless authenticated target/provider evidence establishes it.

## 12. Activation of future physical adapters

Physical execution remains disabled until the owner has the target PC/provider environment and explicitly approves activation after validating:

- target identity;
- executable/package integrity;
- mutual authenticated transport;
- command-signing trust;
- replay protection;
- cancellation/revocation;
- bounded capability map;
- verification/rollback behavior;
- audit evidence;
- recovery from adapter/network failure.

Stage 16 completion therefore means the secure protocol and sandbox are implemented and tested; it does not mean physical autonomous remediation is active.
