# Phase 12 Slice 8 — Live Connector Execution Authority

Phase 12 Slice 8 closes the execution-time specialist-authority gap found in the approved connector-write lifecycle.

Connector writes already had strong controls: enabled connections, provider/action matching, durable idempotent action records, explicit owner approval, task lifecycle, live-write disabled-by-default configuration, OAuth credential status/scope checks, provider-specific target validation, persisted receipts, QA review and independent final verification. The remaining gap was at the final LIVE effect boundary: after approval, `ConnectorActionService.execute(...)` verified only that the task was `RUNNING` before calling `LiveConnectorWriteExecutor`. It did not re-prove that the task was still actively owned by the intended connector execution specialist.

## Execution-time boundary

LIVE connector execution now reuses `DirectExecutionAuthorityService` immediately before the provider executor.

A LIVE connector mutation is eligible only when all of the following are true:

1. the connector action has explicit owner approval;
2. live connector writes are enabled;
3. the durable task is `RUNNING`;
4. the task's current active specialist is exactly `automation-engineer`;
5. `automation-engineer` still owns the exact `automation` tool family in the agent catalog;
6. the durable task mode is exactly `PRIVATE`;
7. the provider credential remains active, unexpired and carries the required provider write scope;
8. the provider-specific target and response checks pass.

Owner approval cannot substitute for specialist authority. If the task is reassigned, paused, its mode changes, or the catalog no longer grants the required tool family, the LIVE provider executor is never called.

Synthetic connector execution remains unchanged. Synthetic receipts are repository/runtime proof artifacts and do not perform a provider mutation, so they do not pretend to require or prove live provider authority.

## Verification and completion

Slice 8 preserves the three-role completion lifecycle established earlier in Phase 12:

- `automation-engineer` executes the governed connector action;
- `qa-engineer` performs the receipt review handoff;
- `mcp-integration-engineer` records the independent PASS decision and completes the task.

A successful provider receipt therefore remains execution evidence, not self-certification.

## OAuth continuity note

The audit also examined OAuth credential rotation. Access-token references are intentionally rotated during normal refresh, so freezing the raw token reference at approval time would incorrectly invalidate legitimate same-account refreshes. The current credential model does not persist a stable provider account subject on `ProviderCredentialEntity`, so Slice 8 does not claim cross-reauthorization account-identity continuity.

Execution still fails closed on revoked/error/expired credentials and missing write scopes. Stable provider-account continuity remains an explicit audit item if Phase 12 requires another slice; it must be implemented using a durable non-secret provider subject/generation, not by treating routine access-token refresh as an identity change.

## Acceptance proof

`Phase12ConnectorLiveAuthorityTest` proves that:

- an authority mismatch blocks the connector action;
- an authority mismatch produces zero `LiveConnectorWriteExecutor` interaction;
- exact `automation-engineer + automation + PRIVATE + RUNNING` authority allows the existing LIVE path to proceed;
- successful execution still records the independent connector receipt decision.

The Phase 12 workflow additionally checks source ordering so `authority.requireRunningSpecialist(...)` occurs before `liveExecutor.execute(...)`.

## Contract and truth boundaries

Slice 8 adds no HTTP endpoint, JPA table, agent ID, connector provider, credential, live-money path or physical-hardware claim. Live connector writes remain disabled by default. Hosted synthetic connector proof remains distinct from live-provider mutation proof.

## Rollback

Revert the `ConnectorActionService` authority dependency/gate, `Phase12ConnectorLiveAuthorityTest`, the Phase 12 workflow additions, and this document as one unit.
