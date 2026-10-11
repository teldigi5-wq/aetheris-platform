# GitHub Branch Protection — verified on 2026-10-11

## Administrative protection

The active ruleset `Protect stable main` (ID `23315877`) targets `main`. It blocks deletion and force-pushes, requires pull requests and resolved review threads, and enforces strict required status checks. These controls have **not** been weakened or changed in this work.

The required contexts currently configured in GitHub are:

```text
canonical-development-line
readiness-contract
dashboard
Analyze JavaScript/TypeScript
core-backend
core-source-independence
release-hardening
contract-freeze
Analyze Core Java
stage26-platform-certification
core-dependency-lockdown
core-reproducible-build
portfolio-evidence
```

These are **the actual ruleset contexts observed**, not a request to create placeholder check jobs. Runtime-owned jobs are not required in the platform ruleset.

## Stage 27 promotion contract

`main` is stable. The canonical development marker remains `feature/syntra-aetheris-foundation-v2`. A PR targeting main may come from that marker or from a reviewed `release/*` branch; do not bypass this by weakening governance or introducing compatibility checks.

## PR #207 review finding

PR [#207](https://github.com/teldigi5-wq/aetheris-platform/pull/207) adds a source-verified Syntra authenticated API contract, but **does not wire authenticated desktop operations**. Its head is `feature/syntra-authenticated-api-contract`. The inspected check-run list for its 2026-09-26 head `456d74217addead38c7543d79f3fa8dca339983e` contains successful CI runs, but did not show the required `canonical-development-line` context. Treat the PR as **not verified for merge** until GitHub's full current required-check evaluation is satisfied; `mergeable_state=clean` is not itself proof that all ruleset checks were emitted.

The contract was pinned to platform SHA `57ad3e0e602efa6feb275328254151b959a3499b`. The separate proposed gateway identity-mapping PR changes the behavior for `/api/orchestrator/memory/**`, so PR #207's `ownerIdHeaderSynthesized=false` will become stale after integration. Reconcile the contract, update the pinned revision and rerun checks in a Stage 27-compliant PR before enabling desktop memory calls.

## Verification checklist

1. Keep the active GitHub ruleset intact.
2. Ensure each named required context appears on the exact PR head with success (not just other green workflows).
3. Confirm Stage 27 canonical-development-line is present and succeeds.
4. Reconcile any conflicting/stale authenticated API contracts and review threads.
5. Verify external runtime certification independently and ensure its source is not copied into platform.
6. Do **not** merge when checks are missing, stale, neutral or failing. No waiver/fake status jobs.

This document is repository governance evidence, not production, PC, site or GPU validation. `BLOCKED_PENDING_HARDWARE` remains authoritative.
