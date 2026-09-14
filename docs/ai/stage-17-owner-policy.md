# Stage 17 Owner Policy — Adapter SDK & Conformance Certification Lab

Stage 17 standardizes adapters without transferring authority away from the owner. Certification proves repository/CI conformance only; it is not permission to activate a physical adapter.

## 1. Owner authority remains above certification

A Stage 17 certificate cannot create or replace:

- Stage 15 recovery approval;
- Stage 16 signed execution-envelope admission;
- owner STOP/PAUSE/TAKE CONTROL;
- target identity validation;
- production deployment approval.

Certification is evidence, not authority.

## 2. Private signing keys

Signing private keys must never be committed to source control, stored in ordinary task/model payloads, printed into CI logs or embedded in adapter manifests.

Stage 17 reuses Stage 16 trusted Ed25519 public keys. Only public-key material/fingerprints and signature hashes belong in the repository/database.

## 3. Signed manifest scope

An adapter manifest may only reduce or describe Stage 16 authority. It may never expand it.

Effective capability is the intersection of:

1. Stage 15 bounded action catalog;
2. Stage 16 adapter capabilities;
3. signed Stage 17 manifest capabilities;
4. current requested operation.

Anything outside that intersection fails closed.

## 4. Forbidden authority

Stage 17 must reject and never certify:

- `ARBITRARY_SHELL`
- `ADMIN_BYPASS`
- `LIVE_ORDER`
- `WITHDRAWAL`
- `TRANSFER`

The adapter SDK must not expose a generic command shell as a workaround for missing capabilities.

## 5. Protocol negotiation

Aetheris must select only a protocol version shared by the client, signed manifest and server ranges.

No silent downgrade or upgrade outside any declared range is allowed.

## 6. Transport honesty

Current Stage 17 mutual-TLS evidence is synthetic. A `SYNTHETIC_MTLS_PASS` means the deterministic transport contract was satisfied in the lab. It does **not** mean a real tunnel, certificate chain or remote target was contacted.

Real transport activation later requires independently measured target/provider evidence.

## 7. Offline queue policy

Offline commands may only reference already-admitted Stage 16 envelopes.

Before simulated reconnect delivery, the queue must recheck:

- underlying envelope still `ADMITTED`;
- envelope not expired;
- owner cancellation not present;
- adapter/manifest policy still permits the action.

Cancellation and revocation outrank reconnect delivery.

## 8. Receipt policy

Repository certification accepts only receipts that are:

- source-measured;
- tied to a Stage 16 completed simulation envelope;
- schema-compatible with the signed manifest;
- exact SHA-256 matches to the Stage 16 sandbox receipt;
- `simulationOnly=true`;
- `targetMutated=false`;
- `externalActionAttempted=false`.

A model or agent may not fabricate a successful receipt.

## 9. Certification policy

The strongest Stage 17 repository status is:

`CERTIFIED_SIMULATION_ONLY`

It requires the complete manifest/protocol/policy/transport/queue/receipt evidence chain.

Certification expires after 30 days so stale evidence cannot remain permanently authoritative.

`productionActivationAllowed=false` is non-negotiable in Stage 17.

## 10. Financial safety

Market data, adapter certification and provider connectivity do not create order authority.

The deterministic Risk Officer remains authoritative. Live orders, withdrawals and transfers remain disabled and outside Stage 17.

## 11. Real target activation prerequisites

A later stage may consider a physical adapter only after all of these exist:

- owner-controlled target PC/provider environment;
- target identity and package integrity evidence;
- production key storage outside Git;
- private transport measured on the real target;
- real cancellation/revocation behavior;
- rollback validation;
- owner approval for activation.

Until then, Stage 17 certification must never be described as physical deployment.
