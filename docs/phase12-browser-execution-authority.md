# Phase 12 Slice 6 — Browser Execution Authority

Phase 12 Slice 6 closes the generic-browser authority gap identified by Slice 5. The repository already had domain allowlists, deterministic owner policy, risk classification, approval gates, emergency stop, download evidence, loopback WebDriver validation and a hard live-money browser block. What was still missing was a durable proof that the `agentId` and `OperationMode` supplied to an enabled browser execution actually belonged to the task that was currently authorized to run.

Slice 6 adds no new browser capability. It binds the existing physical-execution boundary to the shared `DirectExecutionAuthorityService` immediately before owner approval and `W3cWebDriverBrowserAdapter.execute(...)`.

## Execution invariant

Planning remains intentionally backward-compatible and side-effect free. A browser plan may still be evaluated while the local runtime is disabled or physical validation is pending.

When both `aetheris.browser.runtime-enabled=true` and `aetheris.browser.physical-validated=true`, however, no WebDriver execution may occur unless all of the following are true:

1. `taskId` resolves to a durable task;
2. the task is `RUNNING`;
3. the claimed `agentId` is a known catalog specialist;
4. that specialist exactly matches the task's current `activeAgentId`;
5. the specialist owns the exact `browser` tool family in its catalog `allowed-tools`;
6. the request's `OperationMode` exactly matches the task's stored durable mode;
7. deterministic browser policy allows every planned action;
8. any required `browser:workflow` owner approval is present;
9. emergency stop is not active; and
10. the repository/runtime/physical-validation gates are satisfied.

Owner approval cannot override a specialist identity, tool-family or mode mismatch because the direct-authority check occurs before the approval check.

## Fail-closed order

The consequential path is ordered as:

`plan/policy -> runtime truth -> physical truth -> durable specialist authority -> owner approval -> WebDriver adapter`

A failure at the durable-authority boundary returns a blocked browser execution result, records the invocation outcome, and never calls the WebDriver adapter.

The request-supplied mode may still be used to calculate a side-effect-free plan, but it cannot authorize execution by itself. Before the first WebDriver call the same mode must equal the task's stored mode. A caller therefore cannot downgrade a `PRIVATE` or otherwise stricter task by sending a different execution mode.

## Compatibility and physical truth

The default repository behavior is unchanged:

- `runtime-enabled` remains `false` by default;
- `physical-validated` remains `false` by default;
- hardware/browser status remains `BLOCKED_PENDING_HARDWARE` until real owner-PC validation exists;
- existing plan-only tests do not require a durable task;
- runtime-disabled execution still stops at `RUNTIME_UNAVAILABLE` before direct authority is consumed;
- physical-validation-pending execution still stops at `PHYSICAL_VALIDATION_REQUIRED` before direct authority is consumed.

The new unit proof uses a mocked WebDriver adapter with the runtime/physical flags enabled only inside the test object. That proves repository-side authorization order; it is **not** physical-browser evidence.

## Acceptance proof

`Phase12DirectExecutionAuthorityTest` now proves both sides of the browser boundary:

- a physically enabled browser request with matching task, active specialist, `browser` authority and durable mode reaches a mocked WebDriver adapter only after the authority check succeeds;
- Mockito `InOrder` proves the authority check occurs before adapter execution;
- a durable-mode/authority mismatch returns `BLOCKED` and produces zero WebDriver interactions.

The dedicated **Phase 12 Specialist Divisions Proof** also contains a source-order guard that verifies:

`directAuthority.requireRunningSpecialist(...)` appears before `approvals.hasApproved(...)`, which appears before `adapter.execute(...)`.

The same workflow continues to prove tool isolation, governed completion, delegation, scheduler/worker specialist identity, MCP authority and non-simulation host authority.

## Contract impact

Slice 6 adds no HTTP endpoint, JPA table, agent ID, credential, request field, remote transport or execution adapter. It changes only the internal `BrowserOperatorService` dependency graph and execution authorization order.

The Stage 23 compatibility surface is therefore expected to remain unchanged; ordinary Build/contract-freeze CI must still confirm that on the pull request.

## Security boundaries unchanged

Slice 6 does not weaken or bypass:

- browser domain allowlists;
- deterministic policy/risk evaluation;
- protected-data restrictions;
- `browser:workflow` approval;
- emergency stop;
- live-money browser hard block;
- download sandbox/evidence requirements;
- loopback WebDriver endpoint validation;
- independent Phase 12 task verification; or
- the repository's hardware truth boundary.

A successful repository test must not be described as real PC/browser validation.

## Rollback

Revert the browser `DirectExecutionAuthorityService` dependency/check, Slice 6 browser authority tests, the Phase 12 workflow branch/source-order guard and this document as one unit. No schema or request-contract rollback is required.

## Remaining Phase 12 audit

Slice 6 closes the generic-browser direct-effect gap, but it does **not** automatically declare Phase 12 complete. After canonical certification, the next action is a fresh repository-wide audit of the remaining consequential execution entry points that can bypass specialist-bound queue leases. Phase 12 should close only when those paths either already consume the same specialist/policy/approval/verification boundaries or are hardened by another evidence-backed slice.
