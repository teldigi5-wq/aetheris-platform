# Stage 17 — Adapter SDK & Conformance Certification Lab

Stage 17 standardizes how future Aetheris recovery adapters declare capabilities, negotiate protocol versions, prove transport behavior, survive offline/reconnect conditions, emit receipts and earn compatibility certification. It extends Stage 16; it does not bypass Stage 15 approvals or Stage 16 signed-envelope admission.

## Goals

1. Define a signed adapter manifest contract.
2. Negotiate protocol versions without silent downgrade outside declared ranges.
3. Compile capability policy from Stage 16 adapter scope + signed Stage 17 manifest scope.
4. Rehearse TLS 1.3 mutual-auth transport semantics without opening a production network path.
5. Persist offline/reconnect command state and prove cancellation/revocation behavior.
6. Validate measured receipt schemas against Stage 16 sandbox outcomes.
7. Produce an evidence-backed compatibility/certification matrix.
8. Keep physical/production adapter activation disabled until real target validation exists.

## Signed adapter manifests

`Stage17AdapterManifestService` verifies:

- the Stage 16 adapter exists, is enabled and is simulation-only;
- SDK version is semantic-version formatted;
- manifest protocol range is valid;
- manifest capabilities are a subset of the Stage 16 adapter capabilities;
- capabilities remain inside the Stage 15 bounded-action catalog;
- forbidden authorities are rejected;
- declared transport modes come from the Stage 17 allowlist;
- receipt schema is currently `RECEIPT_V1`;
- `manifestSha256` equals the SHA-256 of the canonical manifest content;
- an already-trusted Stage 16 Ed25519 public signer verifies the manifest signature.

The repository stores only public signer material/fingerprints and signature hashes. No signing private key is stored.

## Protocol negotiation

The repository implementation supports protocol versions `1..2`.

Negotiation selects the highest version shared by:

- client range;
- signed manifest range;
- Stage 17 server range.

No overlap returns `INCOMPATIBLE`. It does not silently select a version outside any declared range.

## Capability-policy compiler

`Stage17CapabilityPolicyService` compiles an immutable policy from:

- Stage 16 adapter capabilities;
- signed Stage 17 manifest capabilities;
- requested bounded capabilities.

The policy emits a deterministic SHA-256. Any request containing or exceeding unsupported authority is rejected, including:

- `ARBITRARY_SHELL`
- `ADMIN_BYPASS`
- `LIVE_ORDER`
- `WITHDRAWAL`
- `TRANSFER`

## Synthetic mutual-TLS lab

`Stage17TransportLabService` is intentionally side-effect free. It requires:

- negotiated protocol version;
- signed manifest permission for `SYNTHETIC_MTLS`;
- TLS 1.3;
- mutual authentication;
- client and server certificate pins;
- revocation checks;
- distinct SHA-256 certificate fingerprints.

Successful output is `SYNTHETIC_MTLS_PASS` plus an attestation SHA-256.

Important: this service does **not** open a real network connection and does not claim a private tunnel exists.

## Offline/reconnect queue

`Stage17OfflineQueueService` persists Stage 16 `ADMITTED` envelopes only when the signed manifest allows `OFFLINE_QUEUE` and the capability-policy compiler accepts the exact action.

States:

- `QUEUED_OFFLINE`
- `DELIVERED_SIMULATION`
- `CANCELLED`
- `REVOKED`
- `EXPIRED`

Reconnect behavior rechecks the Stage 16 envelope before simulated delivery. If the underlying envelope was cancelled, expired or is no longer admitted, the queue cannot deliver it.

Stage 17 queue delivery means only that the command reached the simulation boundary. It does not execute the Stage 16 command and does not mutate a target.

## Cancellation/revocation conformance

An adapter has sufficient queue-conformance evidence only after the journal contains:

- at least one `DELIVERED_SIMULATION` event; and
- at least one `CANCELLED` or `REVOKED` event; and
- zero external-action attempts.

This makes reconnect behavior and owner cancellation/revocation observable before certification.

## Measured receipt schema

`Stage17MeasuredReceiptService` accepts a receipt only when:

- its Stage 16 envelope is `SIMULATION_COMPLETED`;
- schema matches the signed manifest;
- outcome matches the Stage 16 envelope outcome;
- receipt SHA-256 matches the Stage 16 sandbox receipt;
- evidence is source-measured;
- `simulationOnly=true`;
- `targetMutated=false`;
- `externalActionAttempted=false`.

The current repository path therefore cannot certify a receipt that claims real target mutation.

## Certification

`Stage17CertificationService` requires all of the following:

1. latest verified signed manifest;
2. successful protocol negotiation;
3. compiled capability policy;
4. synthetic mTLS pass;
5. offline/reconnect queue conformance;
6. measured simulation receipt belonging to the same adapter.

Successful repository certification is:

`CERTIFIED_SIMULATION_ONLY`

with score `100` and a 30-day evidence expiry.

`productionActivationAllowed` is always `false` in Stage 17.

## Compatibility matrix

The compatibility endpoint reports per adapter:

- Stage 16 adapter type;
- simulation-only state;
- Stage 17 SDK version;
- protocol range;
- latest certification status/score/expiry;
- production activation status.

The matrix is evidence reporting, not activation.

## API surface

Base path: `/api/orchestrator/stage17`

Key endpoints:

- `POST /manifests`
- `GET /manifests`
- `GET /manifests/{adapterId}/latest`
- `POST /protocol/negotiate`
- `POST /policy/compile`
- `POST /transport/rehearse`
- `POST /queue/{envelopeId}`
- `POST /queue/{queueId}/cancel`
- `POST /queue/reconnect`
- `POST /queue/revoke`
- `GET /queue`
- `GET /queue/{adapterId}/conformance`
- `POST /receipts`
- `GET /receipts`
- `POST /certifications`
- `GET /certifications`
- `GET /compatibility`
- `GET /overview`

The owner-facing `/stage17.html` page is read-only; it has no registration, execute, reconnect, revoke or certification button.

## CI validation

`Stage17IntegrationTest` covers:

- valid signed manifest verification;
- tampered signature rejection;
- protocol negotiation and incompatible ranges;
- capability-policy rejection for forbidden authority;
- TLS 1.3 synthetic mTLS requirements;
- offline queue reconnect with no external action;
- Stage 16 cancellation propagation into queue state;
- Stage 17 revocation semantics;
- queue conformance evidence;
- measured receipt correlation;
- simulation-only certification and compatibility matrix.

## External validation still pending

Stage 17 does not claim:

- target-PC adapter installation;
- real mutual-TLS transport;
- hardware-backed key storage;
- real provider adapter connectivity;
- real Windows/service mutation;
- real notification delivery;
- real exchange-testnet execution;
- production adapter certification.

Those require real target/provider evidence in later stages.
