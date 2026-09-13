# Stage 12 — Production Integration Fabric

Stage 12 creates the controlled boundary between the Syntra + Aetheris platform and real external providers without weakening the safety, privacy, workstation or financial invariants established in Stages 1–11.

The design is evidence-first: **configured is not connected, connected is not healthy, healthy is not authorized, and authorization is not proof that an external action occurred.**

## Delivered components

### Durable provider registry

Stage 12 persists non-secret provider metadata for four provider classes:

- `NOTIFICATION`
- `MARKET_DATA`
- `EXCHANGE_TESTNET`
- `PRIVATE_TRANSPORT`

The registry stores endpoint, credential alias, enabled state, status, capabilities, read-only flag and update time. It does not store credential values.

Registration is fail-closed:

- remote providers require HTTPS;
- endpoint user-info/embedded credentials are rejected;
- market-data providers must be read-only;
- exchange providers must explicitly use a testnet/sandbox endpoint;
- `LIVE_ORDER`, `WITHDRAWAL`, `TRANSFER`, `ARBITRARY_SHELL` and `ADMIN_BYPASS` capabilities are rejected;
- exchange-testnet capabilities are limited to `READ_ACCOUNT`, `READ_MARKET`, `TEST_ORDER` and `CANCEL_TEST_ORDER`; and
- private-transport capabilities remain narrowly scoped.

A newly enabled provider begins as `CONFIGURED_PENDING_PROVIDER_EVIDENCE`; configuration alone never produces a healthy state.

### Provider-measured health

A provider can move to `PROVIDER_REPORTED_HEALTHY`, `PROVIDER_REPORTED_DEGRADED` or `PROVIDER_REPORTED_UNAVAILABLE` only when health evidence is explicitly marked provider-measured and carries a valid SHA-256 attestation reference.

CI/configuration is not allowed to self-certify provider health.

### Cryptographic release trust

Stage 12 adds a release-promotion gate based on:

1. owner-configured Ed25519 public key;
2. owner-configured SHA-256 fingerprint of that public key;
3. signed release manifest;
4. observed artifact SHA-256 matching the manifest; and
5. a valid Ed25519 signature over the canonical payload:

`version|artifactSha256|CHANNEL`

Channels are limited to `CANARY` and `STABLE`.

The readiness endpoint exposes only non-secret trust state: whether the signer fingerprint/public key are configured and internally consistent. No private signing key is required or exposed by the runtime verifier.

### Exact target-attestation correlation

Stage 12 consumes the durable Stage 11 target-evidence ledger, but it does not accept “any historical evidence of this kind.”

A downstream Stage 12 check must provide the **exact expected SHA-256 attestation**. The service verifies that Stage 11 contains a target-measured, `TARGET_REPORTED:*` evidence record of the requested kind with that exact hash.

This prevents stale or unrelated workstation/provider evidence from authorizing a new operation.

### Private-transport eligibility

The Stage 12 private-transport gate requires all of the following before a controlled deployment test is eligible:

- registered provider of type `PRIVATE_TRANSPORT`;
- provider enabled;
- provider-reported healthy evidence;
- TLS 1.3;
- mutual device authentication;
- private tunnel/network evidence;
- revocation check;
- certificate SHA-256 pin;
- target-measured attestation; and
- no raw public general-purpose agent API.

A successful gate returns `ELIGIBLE_FOR_PRIVATE_TRANSPORT_TEST`. It deliberately keeps `productionTunnelActive=false`; policy evidence cannot claim that a real production tunnel is already running.

### Notification integration boundary

A notification provider requires:

- enabled `NOTIFICATION` provider;
- provider-reported healthy evidence;
- vault credential alias;
- `SEND_NOTIFICATION` capability.

Readiness only permits a controlled provider test. Delivery truth must arrive as provider-measured `DELIVERED`, `FAILED` or `DEFERRED` evidence with a SHA-256 attestation. Credential values are never returned through this boundary.

Stage 12 does not invent or simulate production delivery confirmation.

### Market-data integration boundary

Market sources must be:

- registered as `MARKET_DATA`;
- enabled;
- provider-reported healthy;
- read-only;
- configured with a vault credential alias; and
- granted `READ_MARKET` only as required by the integration gate.

Provider-measured market snapshot evidence must be fresh (maximum 60 seconds in the current Stage 12 contract) and carry a SHA-256 attestation.

Market data has no authority to create orders or change trading risk limits.

### Exchange-testnet boundary

Exchange integration remains **testnet/sandbox only**.

Required characteristics:

- `EXCHANGE_TESTNET` provider type;
- HTTPS testnet/sandbox endpoint;
- provider-reported healthy evidence;
- vault credential alias;
- `TEST_ORDER` capability for a controlled test;
- no production endpoint;
- no `LIVE_ORDER` capability;
- no withdrawal capability; and
- no transfer capability.

The readiness object always exposes:

- `externalActionAttempted=false`
- `liveMoneyEnabled=false`
- `withdrawalOrTransferEnabled=false`

Stage 9 deterministic trading/risk authority remains in force. Stage 12 provider plumbing does not override the Risk Officer.

## Stage 12 API surface

Base path: `/api/orchestrator/stage12`

Key endpoints include:

- `GET /providers`
- `POST /providers`
- `POST /providers/{providerId}/health`
- `GET /release/readiness`
- `POST /release/verify`
- `GET /target-attestation/{kind}?attestationSha256=...`
- `POST /transport/evaluate`
- `GET /notification/{providerId}/readiness`
- `POST /notification/{providerId}/delivery-evidence`
- `GET /market/{providerId}/readiness`
- `POST /market/{providerId}/snapshot-evidence`
- `GET /exchange-testnet/{providerId}/readiness`

The controller exposes policy/evidence contracts. It does not itself perform live external provider actions.

## Stage 12 console

`/stage12.html` is a read-only integration evidence console.

It displays:

- durable provider registry entries;
- provider-reported health states;
- credential aliases, never credential values;
- release trust-anchor readiness;
- exact target-attestation lookup;
- private-transport requirements;
- notification evidence requirements;
- read-only market-data requirements; and
- exchange-testnet financial boundaries.

The page deliberately has no secret-entry, provider-enablement, notification-send, tunnel-open or order-submit control.

## Regression evidence

The Stage 12 integration tests prove that:

- production exchange endpoints are rejected;
- forbidden live-money capabilities are rejected;
- provider health cannot be certified by CI;
- credential values are not exposed;
- a real ephemeral Ed25519 key pair can sign a test release manifest and pass the verifier;
- a tampered artifact hash fails release verification;
- target proof must match the exact expected attestation SHA-256;
- unrelated historical target evidence cannot satisfy a new gate;
- private-transport eligibility requires target evidence and never claims an active production tunnel;
- notification and market evidence remain provider-measured; and
- exchange-testnet readiness keeps live money, withdrawals and transfers disabled.

Run 308 exposed that the first target-attestation lookup was too broad because unrelated historical Stage 11 target evidence could satisfy a kind-only query. The implementation was tightened to exact SHA-256 correlation rather than isolating or weakening the test. Run 311 passed the corrected Stage 12 core together with all earlier regressions, the dashboard and the Windows workstation-agent gate.

## External evidence still required

Repository/CI completion does **not** claim that any real provider account is connected. Owner/provider-specific work still includes:

- entering fresh provider credentials through the vault;
- registering real notification/market/testnet/private-transport providers;
- obtaining provider-measured health evidence;
- performing an owner-approved notification delivery test;
- validating redundant market-source quality/freshness;
- validating the private tunnel and certificate chain on the target environment;
- performing controlled exchange-testnet order/fill/reject/fee tests; and
- configuring the owner release trust anchor for real signed releases.

These remain explicit deployment/provider tasks and must not be represented as completed until their evidence exists.

## Financial invariant

Stage 12 does not implement live-money execution.

- Live orders: disabled.
- Withdrawals: disabled.
- Transfers: disabled.
- Production exchange endpoints: rejected by the Stage 12 registry.
- Deterministic Risk Officer: still authoritative.

## Next-stage direction

A future Stage 13 can build the controlled deployment/evidence automation above this fabric: owner-approved provider onboarding workflows, credential-vault alias binding, signed release canary/rollback orchestration, provider health polling, redundant source arbitration, controlled notification delivery verification and exchange-testnet execution journaling. Such a stage must preserve all Stage 12 boundaries and remain incapable of silently escalating to live-money authority.