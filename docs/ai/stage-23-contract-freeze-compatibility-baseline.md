# Stage 23 — Contract Freeze & Compatibility Baseline

Stage 23 is a repository-only compatibility milestone. It does not advance the Stage 21 physical target pilot while the owner's Windows PC is unavailable.

## Goal

Freeze the repository-visible control-plane contract behind a deterministic SHA-256 so accidental API, persistence, agent-catalog, workflow or evidence-surface drift becomes a CI failure instead of silently changing behavior in a long-running branch.

## Contract inventory

`scripts/stage23/contract_baseline.py` deterministically inventories:

- Spring `GET/POST/PUT/DELETE/PATCH` controller mappings across gateway, user, identity, audit and orchestrator services;
- explicit JPA `@Table(name=...)` declarations;
- configured Aetheris specialist-agent IDs;
- named jobs in `.github/workflows/build.yml`;
- Stage evidence-console HTML files;
- the Stage 23 hard-boundary payload.

The canonical payload excludes the source commit from the hash so documentation-only or unrelated source changes do not alter the compatibility SHA. The emitted evidence still records the exact source commit separately.

## Compatibility guard

`scripts/stage23/contract_guard.py` fails closed when:

- the endpoint/table/agent inventory becomes unexpectedly small;
- duplicate HTTP method/path mappings are detected;
- duplicate explicit JPA table names are detected;
- Stage 18–21 API prefixes disappear;
- forbidden withdrawal/transfer/live-order Java API contracts appear;
- required CI jobs disappear;
- Stage 21–23 evidence consoles disappear;
- the hard-boundary payload changes;
- testnet execution no longer defaults to false;
- Stage 22 gains merge/auto-merge authority;
- Stage 21 hardware-boundary state is removed;
- the generated contract SHA differs from the owner-reviewed pinned SHA.

## Two-pass pinning

Stage 23 intentionally closes in two CI passes.

1. **Bootstrap pass** — the expected SHA file contains `BOOTSTRAP_PENDING`; CI generates and validates the contract, prints the deterministic SHA and uploads the evidence.
2. **Freeze pass** — the observed SHA from the green bootstrap run is committed to `scripts/stage23/expected-contract.sha256`. CI reruns with exact-hash enforcement.

After the freeze pass, intentional compatibility changes require an explicit source-control update to the pinned hash and therefore become reviewable events.

## Hardware and financial boundary

The frozen payload requires:

- `physicalStage21BlockedPendingHardware=true`
- `productionActivationAllowed=false`
- `liveMoneyOrdersAllowed=false`
- `withdrawalsAllowed=false`
- `transfersAllowed=false`
- `unrestrictedShellAllowed=false`
- `adminBypassAllowed=false`

A green Stage 23 job is evidence that repository contracts match the pinned baseline. It is not evidence that a workstation exists, that Stage 21 has passed physically, that PR #7 may be merged, or that any production/financial authority is enabled.
