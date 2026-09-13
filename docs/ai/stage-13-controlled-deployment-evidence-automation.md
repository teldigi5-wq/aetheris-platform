# Stage 13 — Controlled Deployment & Evidence Automation

Stage 13 builds a governed automation layer above Stage 12. Its purpose is to automate readiness checks, evidence correlation, owner-approval transitions and durable journals without turning CI/configuration into proof that an external provider, target workstation or production deployment is active.

## Goals

Stage 13 adds:

- durable provider-health evidence windows;
- credential-vault alias readiness without reading secret values;
- owner-approved provider onboarding plans;
- signed canary eligibility with exact target proof and rollback requirements;
- redundant read-only market-source arbitration; and
- exchange-testnet intent/evidence journaling.

It does **not** add live-money authority, withdrawals, transfers, unrestricted shell execution or automatic production promotion.

## Provider health evidence

`Stage13ProviderHealthEvidenceService` stores provider-measured health observations separately from Stage 12 configuration state.

A health window is ready only when:

- the Stage 12 provider is enabled;
- Stage 12 already has provider-reported `HEALTHY` state;
- at least three Stage 13 samples exist in the last five minutes;
- at least two recent samples are `HEALTHY`; and
- the latest sample is not `UNAVAILABLE`.

Each sample includes a SHA-256 attestation reference, measured latency and observation timestamp. Configuration or CI-only claims cannot create provider-measured evidence.

## Credential binding

`Stage13CredentialBindingService` inspects credential aliases through `CredentialVault.describe(alias)` only.

It does not call `resolve(alias)` and therefore does not retrieve provider secret values for readiness checks.

Possible readiness levels include:

- `CREDENTIAL_ALIAS_REQUIRED`
- `CREDENTIAL_EVIDENCE_REQUIRED`
- `CONTROLLED_TEST_ALIAS_AVAILABLE`
- `OS_VAULT_ALIAS_AVAILABLE`

An environment/test vault can validate software wiring in CI, but it cannot be represented as proof that Windows DPAPI or another OS-backed vault is working on the owner's future workstation.

## Provider onboarding lifecycle

`Stage13OnboardingAutomationService` uses durable plans tied to existing Aetheris tasks.

Lifecycle:

`EVIDENCE_REQUIRED`
→ `AWAITING_OWNER_APPROVAL`
→ `CONTROLLED_TEST_AUTHORIZED_NOT_EXECUTED`
→ `CONTROLLED_TEST_VERIFIED` or `ROLLBACK_REQUIRED`

The owner approval must:

- be `APPROVED`;
- use action type `STAGE13_PROVIDER_ONBOARD`; and
- belong to the same task as the onboarding plan.

A passing controlled-test result must be provider-measured, carry a SHA-256 attestation and preserve rollback availability.

## Signed canary gate

`Stage13CanaryDeploymentService` evaluates whether a canary is eligible. It does not perform deployment.

Eligibility requires:

1. owner approval using `STAGE13_CANARY_DEPLOY` on the same task;
2. a Stage 12 `CRYPTOGRAPHICALLY_VERIFIED` Ed25519 release manifest;
3. the exact expected Stage 11 target attestation SHA-256;
4. a valid rollback artifact SHA-256; and
5. a rollback artifact distinct from the candidate artifact.

A successful result is:

`CANARY_ELIGIBLE_NOT_EXECUTED`

The result keeps `externalActionAttempted=false` and `productionPromoted=false`.

## Redundant market arbitration

`Stage13RedundantMarketArbitrationService` is read-only.

It requires at least two independent `MARKET_DATA` providers. Each source must:

- remain read-only;
- have a ready Stage 13 health window;
- provide provider-measured evidence;
- provide a positive price;
- be no older than five seconds; and
- include SHA-256 evidence.

The service calculates a median and blocks the source set when maximum deviation from the median exceeds 1.50%.

A successful result is `REDUNDANT_READ_ONLY_CONSENSUS` and always keeps trading/order authority false.

## Exchange-testnet journal

`Stage13TestnetExecutionJournalService` records owner-authorized testnet intent and provider-reported testnet evidence.

Creating intent requires:

- Stage 12 testnet/sandbox readiness;
- owner approval action `STAGE13_TESTNET_ORDER` on the same task;
- a unique client order ID;
- valid side/order type/positive quantity; and
- a SHA-256 reference to the deterministic risk decision.

Initial state:

`TESTNET_INTENT_AUTHORIZED_NOT_EXECUTED`

Provider evidence may later record:

- `ACCEPTED`
- `PARTIALLY_FILLED`
- `FILLED`
- `REJECTED`
- `CANCELLED`

The journal itself never submits an order. It keeps:

- `liveMoneyEnabled=false`
- `withdrawalOrTransferEnabled=false`
- `externalActionAttempted=false`

Stage 9 deterministic risk controls remain authoritative.

## API surface

Stage 13 is exposed under `/api/orchestrator/stage13`.

Key endpoints:

- `POST /provider-health/evidence`
- `GET /provider-health/{providerId}`
- `GET /credentials/{providerId}`
- `POST /onboarding`
- `POST /onboarding/{planId}/assess`
- `POST /onboarding/{planId}/authorize/{approvalId}`
- `POST /onboarding/{planId}/evidence`
- `GET /onboarding`
- `POST /canary/evaluate`
- `POST /market/arbitrate`
- `POST /testnet/intents`
- `POST /testnet/{journalId}/evidence`
- `GET /testnet`

The Stage 13 console at `/stage13.html` is intentionally read-only for deployment/provider/testnet actions.

## CI coverage

`Stage13IntegrationTest` verifies:

- health windows fail closed when evidence is insufficient;
- credential readiness does not expose secrets;
- onboarding requires evidence and task-bound owner approval;
- controlled-test PASS requires rollback;
- read-only market consensus cannot gain trading/order authority;
- divergent market sources are blocked;
- signed canary eligibility requires a real Ed25519 test signature, exact target proof, owner approval and rollback; and
- testnet journaling keeps live money, withdrawals and transfers disabled.

The first Stage 13 core passed Build run 318 across backend, dashboard and the Windows workstation-agent job.

## External validation still pending

Until the owner has the target PC and connects real providers, repository/CI completion must not be interpreted as proof of:

- physical Windows agent installation;
- owner-profile DPAPI storage;
- real local voice/GPU/high-refresh performance;
- active private TLS transport;
- real provider authentication;
- real notification delivery;
- redundant real market-feed quality;
- a submitted exchange-testnet order/fill/reject/fee cycle; or
- production signing/deployment.

These remain evidence-gated deployment tasks.

## Merge readiness note

Before this long-running draft PR is eventually merged, the repository's `main` branch should be protected and required status checks should be configured so future direct or force changes cannot bypass the validated workflow.
