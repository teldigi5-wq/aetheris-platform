# Stage 13 — Owner Policy

Stage 13 automates deployment readiness and evidence processing while preserving the owner's final authority and the deterministic safety boundaries established in earlier stages.

## Automatically allowed

The system may automatically:

- read Stage 12 provider metadata and non-secret credential aliases;
- inspect credential descriptors without resolving secret values;
- store provider-measured health observations;
- calculate health-window readiness;
- compute read-only redundant market consensus;
- evaluate release/canary eligibility from already supplied evidence;
- list onboarding plans and testnet journals;
- evaluate whether required approvals exist; and
- run Stage 13 regression/security tests.

## Owner approval required

Owner approval is required before a Stage 13 workflow is authorized for any controlled external test.

The approval must be tied to the same durable task and use the exact action type expected by the workflow:

- `STAGE13_PROVIDER_ONBOARD`
- `STAGE13_CANARY_DEPLOY`
- `STAGE13_TESTNET_ORDER`

An approval for one task/action cannot authorize another task/action.

## Strong confirmation required

Strong confirmation is required before any future implementation attempts to:

- perform an actual canary deployment or rollback on the target workstation;
- open or change a private production transport;
- rotate a production trust anchor/device certificate;
- change firewall/UAC/security controls;
- broaden an exchange credential beyond testnet permissions;
- alter deterministic trading risk limits; or
- introduce any live-money order authority.

Stage 13 does not implement these actions.

## Secrets

Stage 13 readiness logic may inspect credential descriptors and aliases only.

It must not:

- log, persist or return provider secret values;
- use credential values as ordinary task/model context;
- embed credentials in URLs;
- treat an environment/test vault as proof that an OS-backed vault has been validated; or
- reuse credentials copied from chat, logs, documents or source code.

Production/testnet credentials must be newly supplied by the owner through the appropriate vault path when the real provider is connected.

## Provider evidence

Provider configuration is not provider health.

Provider health is not a controlled-test result.

A controlled-test result is not production deployment proof.

The evidence progression remains:

`CONFIGURATION`
→ `PROVIDER-MEASURED HEALTH`
→ `OWNER-APPROVED CONTROLLED TEST`
→ `DOMAIN-SPECIFIC EVIDENCE`
→ `ELIGIBLE FOR NARROW USE`

Missing, stale, degraded, contradictory or unrelated evidence must fail closed.

## Canary policy

Canary eligibility requires all of the following:

- task-bound owner approval;
- cryptographically verified Stage 12 release manifest;
- exact target attestation SHA-256;
- rollback artifact SHA-256; and
- rollback artifact different from the candidate artifact.

`CANARY_ELIGIBLE_NOT_EXECUTED` means only that deterministic prerequisites are satisfied. It does not mean a package was installed, a service was restarted or production was promoted.

## Market-data policy

Market arbitration is read-only.

It must not:

- submit orders;
- modify positions;
- alter leverage/risk limits;
- choose an exchange execution path; or
- claim certainty when sources disagree.

Stage 13 blocks consensus when independent source divergence exceeds 1.50% or source health/freshness/evidence requirements fail.

## Testnet policy

Stage 13 exchange activity remains testnet/sandbox-only.

Allowed intent/journal context may reference:

- `READ_ACCOUNT`
- `READ_MARKET`
- `TEST_ORDER`
- `CANCEL_TEST_ORDER`

The following remain prohibited:

- `LIVE_ORDER`
- `WITHDRAWAL`
- `TRANSFER`

The testnet journal does not itself submit an exchange order. It records approved intent and provider-reported evidence.

Stage 9 deterministic Risk Officer remains authoritative before any controlled testnet execution adapter is ever connected.

## Emergency controls

`STOP ALL`, `PAUSE` and `TAKE CONTROL` remain higher priority than:

- provider health polling;
- onboarding automation;
- release/canary evaluation;
- market-source arbitration;
- notifications;
- testnet evidence processing;
- model inference; and
- background jobs.

A provider, target or release incident must preserve audit evidence and rollback/revocation material while disabling affected automation.

## External validation honesty

Because the target PC and real provider connections are not yet available, Stage 13 repository/CI completion must not be described as successful real-world deployment.

Physical PC, Windows DPAPI, local voice/GPU performance, private transport, real provider authentication, live notification delivery, redundant real market feeds and real exchange-testnet execution remain external evidence tasks.

## Repository protection

Before merging the long-running Syntra/Aetheris feature PR, protect `main` and require the validated status checks. Stage completion does not authorize bypassing branch protection or force-pushing validated history.
