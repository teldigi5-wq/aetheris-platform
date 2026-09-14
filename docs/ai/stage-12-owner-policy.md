# Stage 12 — Owner Policy

Stage 12 connects production-facing integration contracts to external providers while preserving owner control, least privilege, privacy, evidence integrity and the financial boundaries established in Stages 1–11.

## Automatic / read-only

The system may automatically:

- list registered provider metadata and non-secret credential aliases;
- read provider status and prior provider-health evidence;
- evaluate release trust-anchor readiness without exposing signing secrets;
- verify a supplied signed release manifest and observed artifact hash;
- verify whether an exact Stage 11 target attestation exists;
- evaluate private-transport eligibility from supplied evidence;
- evaluate notification, market-data and exchange-testnet readiness;
- read controlled delivery/market evidence; and
- run Stage 12 regression/security tests.

## Owner approval required

Owner approval is required before:

- registering or enabling a real external provider;
- binding a provider to a credential-vault alias;
- rotating a provider credential alias;
- changing provider endpoint/capabilities;
- marking a provider health check as target/provider-measured;
- configuring or replacing the trusted release signing public key/fingerprint;
- pairing or revoking private-transport devices;
- opening a private tunnel for a controlled deployment test;
- sending a real notification delivery test;
- adding or removing a real market-data source; or
- enabling a real exchange-testnet adapter/order test.

## Strong confirmation required

Strong confirmation is required before any future workflow attempts to:

- change security/firewall/tunnel trust settings on the target environment;
- rotate critical trust anchors or device certificates;
- alter deterministic trading risk limits;
- broaden exchange credentials/capabilities;
- switch an exchange integration away from testnet/sandbox; or
- introduce live-money authority.

Stage 12 itself rejects production exchange endpoints and has no live-money capability.

## Prohibited

The system must not:

- store or display external provider secret values in the Stage 12 registry, console or ordinary evidence logs;
- embed credentials in provider URLs;
- treat configuration as provider health;
- let CI/configuration self-certify provider-measured health;
- let unrelated historical Stage 11 evidence satisfy a new Stage 12 target proof;
- accept target proof without an exact expected SHA-256 correlation;
- promote a release without matching artifact hash, trusted signer fingerprint and valid Ed25519 signature;
- expose or require the release private signing key inside the runtime verifier;
- expose a raw public general-purpose agent API through private transport;
- let market-data integrations submit orders or change risk policy;
- register or enable `LIVE_ORDER`, `WITHDRAWAL` or `TRANSFER` capabilities;
- accept a production exchange endpoint as `EXCHANGE_TESTNET`;
- let a provider/model/agent self-promote its own health, permissions or deployment state; or
- relabel missing/failed evidence as verified.

## Evidence precedence

For external integrations, evidence is interpreted in this order:

1. domain-specific provider/target measured evidence tied to the exact expected attestation;
2. controlled owner-approved integration-test evidence;
3. cryptographically verified artifact/release evidence;
4. CI/software regression evidence; and
5. configuration/intention only.

A lower tier may prove code behavior but cannot silently promote itself into a higher-tier deployment claim.

## Provider lifecycle

The expected provider lifecycle is:

`REGISTERED / CONFIGURED`
→ `PROVIDER-MEASURED HEALTH`
→ `OWNER-APPROVED CONTROLLED TEST`
→ `DOMAIN EVIDENCE`
→ `ELIGIBLE FOR USE WITHIN ITS NARROW CAPABILITY`

At every step, revocation, disablement, health degradation or missing attestation must fail closed.

## Financial boundary

Exchange providers are testnet/sandbox only.

Allowed Stage 12 exchange-testnet capabilities are narrowly limited to:

- `READ_ACCOUNT`
- `READ_MARKET`
- `TEST_ORDER`
- `CANCEL_TEST_ORDER`

The following remain prohibited:

- `LIVE_ORDER`
- `WITHDRAWAL`
- `TRANSFER`

Stage 9 deterministic risk controls remain authoritative even for future controlled testnet execution.

## Emergency controls

`STOP ALL`, `PAUSE` and `TAKE CONTROL` remain higher priority than provider polling, release verification, notifications, market ingestion, testnet activity, model inference or background automation.

A provider/tunnel/release incident must disable the affected external integration while preserving audit evidence, revocation state, target attestations and rollback material.