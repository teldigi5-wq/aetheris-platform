# Phase 12 Slice 9 — Safe Tool Execution Authority

Phase 12 Slice 9 closes the remaining caller-selected specialist gap in the generic safe-tool execution path.

The safe-tool registry already enforced exact tool-family authorization before owner policy, but `ToolExecutionService` previously accepted `taskId`, `agentId` and `OperationMode` as independent request fields. A caller could therefore name a specialist that owned a registered tool even when that specialist was not the durable task's current execution specialist. The same request-supplied mode was also used for owner-policy evaluation without first proving it matched the task's stored mode.

Slice 9 does not add a tool, endpoint, agent, credential, shell primitive or external write capability. It makes existing safe-tool adapters consume the same durable task authority used by the other Phase 12 direct-effect boundaries.

## Execution-time boundary

Before owner policy, owner approval, or a tool adapter can execute, `ToolExecutionService` now:

1. resolves the registered tool and its explicit specialist tool family;
2. preserves emergency-stop precedence so an active stop cancels the invocation without requiring live authority;
3. requires a durable task context;
4. requires the task to be `RUNNING`;
5. requires the request `agentId` to exactly equal the task's current `activeAgentId`;
6. requires that specialist to still own the exact tool family in the agent catalog;
7. when the caller supplies a mode, requires it to exactly match the task's durable mode;
8. evaluates ordinary owner policy using the **durable task mode**, not a caller-selected fallback;
9. preserves any required exact owner approval;
10. only then invokes filesystem, terminal or GitHub adapters.

A missing task, reassigned task, paused task, tool-family mismatch or mode mismatch returns a fail-closed `BLOCKED` execution result and no tool adapter is invoked.

## Durable mode is authoritative

`ToolExecutionRequest.mode` remains nullable for compatibility. When it is omitted, the task's stored mode is used for policy evaluation. When it is supplied, it must match the durable task mode.

This prevents a PRIVATE task from being evaluated as BALANCED merely because a caller omitted or changed the mode field.

## Existing controls preserved

Slice 9 preserves all earlier boundaries:

- `SpecialistToolAuthorizationService` still owns the explicit tool-id to tool-family mapping and contains no wildcard grant;
- `SafeToolRegistryService` still evaluates the specialist boundary before owner policy;
- workspace path traversal, write-enable and size limits are unchanged;
- terminal command/working-directory/timeout allowlists are unchanged;
- GitHub repository allowlists and scoped credential access are unchanged;
- GitHub proposal publishing still requires Slice 7 publish-time specialist revalidation plus exact proposal approval;
- emergency stop remains authoritative;
- task completion still requires the independent-verification boundary;
- live-money, unrestricted shell, admin bypass and production activation boundaries are unchanged.

## Acceptance proof

`Phase12SafeToolExecutionAuthorityTest` proves that:

- a caller cannot borrow another specialist identity for a real task;
- an authority mismatch blocks before owner-policy evaluation or any filesystem/terminal/GitHub adapter interaction;
- when request mode is omitted, owner policy receives the task's durable mode;
- specialist authority succeeds before owner approval is checked;
- denied owner approval still prevents the mutation adapter from running.

The Phase 12 workflow additionally verifies source order:

`task-specialist authority -> durable-mode policy -> owner approval -> tool adapter`

and continues to run every prior Phase 12 specialist-governance suite.

## Contract and truth boundaries

No HTTP endpoint, JPA table/field, agent ID, credential, tool descriptor or external adapter is added. No physical-PC/browser validation is claimed. The Stage 23 compatibility surface should remain unchanged.

## Rollback

Revert the `ToolExecutionService` authority/mode binding, `Phase12SafeToolExecutionAuthorityTest`, the Phase 12 workflow additions and this document as one unit.

## Remaining Phase 12 audit

After this slice, the remaining specialist-governance audit should focus on identity continuity rather than caller-selected execution identity. In particular, Slice 8 documented that OAuth access-token references legitimately rotate during refresh, while the current connector credential model does not yet prove stable provider-account subject continuity across a full reauthorization. Any follow-up must use a durable non-secret provider identity/generation rather than freezing access-token references.
