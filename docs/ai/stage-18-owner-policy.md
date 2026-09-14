# Stage 18 Owner Policy — Target Readiness & Provisioning Orchestrator

Stage 18 prepares evidence for a future real Windows target. It does not grant target activation authority.

## 1. Owner remains final authority

A Stage 18 score of 100 means only `READY_FOR_OWNER_ACTIVATION_REVIEW`.

It does not mean:

- activate the Windows adapter;
- install software automatically;
- elevate privileges;
- create unrestricted shell access;
- enable live-money trading;
- allow withdrawals or transfers.

Any later physical activation must be a separate owner-approved stage and must re-check fresh evidence.

## 2. Evidence sources are not interchangeable

`PASS_SIMULATED` is never equal to target-measured `PASS`.

CI can prove code paths, cryptographic checks and deterministic state transitions. CI cannot prove:

- the owner's future PC identity;
- physical Windows package installation;
- DPAPI availability in the owner's profile;
- local GPU/RAM/model performance;
- microphone/speaker performance;
- private transport on the owner's actual network;
- provider credential availability.

## 3. Exact identity correlation

A target is only considered identity-ready when measured evidence is bound to the exact certificate fingerprint in the signed bootstrap manifest.

Package readiness requires the exact host-agent package SHA-256 in the signed bootstrap manifest.

Similar, older or unrelated hashes must fail closed.

## 4. Signing keys

Stage 18 uses the existing trusted Ed25519 public-key registry. Private signing keys must not be committed to Git, stored in ordinary database rows, printed in logs, or placed in evidence detail.

## 5. Secret handling

Provider evidence stores aliases and attestations, never credential values.

Evidence detail containing obvious credential markers such as API keys, authorization headers, bearer tokens, passwords or private keys is rejected.

Future provider onboarding must continue through the vault abstraction.

## 6. Capability boundary

Stage 18 target manifests may declare only the bounded readiness/runtime capabilities explicitly allowlisted by the Stage 18 service.

The following remain forbidden:

- `ARBITRARY_SHELL`
- `ADMIN_BYPASS`
- `DISABLE_SECURITY`
- `UAC_BYPASS`
- `CREDENTIAL_EXPORT`
- `LIVE_ORDER`
- `WITHDRAWAL`
- `TRANSFER`

A manifest is readiness intent, not permission to execute an action.

## 7. STOP / PAUSE / TAKE CONTROL

Existing deterministic owner controls continue to outrank background work. Stage 18 introduces no execution loop that can override STOP, PAUSE or TAKE CONTROL.

## 8. Benchmark honesty

Resource, speech and local-model results only count toward target readiness when `measuredOnTarget=true` and the deterministic benchmark gate passes.

Do not manually convert CI benchmark evidence into target evidence.

## 9. Stage 17 certification dependency

A future target must use an adapter with a current Stage 17 `CERTIFIED_SIMULATION_ONLY` certificate before Stage 18 can reach 100/100. Stage 17 certification still does not authorize physical activation; it only proves the adapter protocol passed the simulation conformance lab.

## 10. Production activation remains false

Every Stage 18 readiness response, evidence bundle and provisioning rehearsal reports production activation as false.

The next physical activation stage must be explicit, separately reviewed and owner-approved.
