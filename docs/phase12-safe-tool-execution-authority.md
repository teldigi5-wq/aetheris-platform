# Phase 12 Slice 9 — Safe Tool Execution Authority

Phase 12 Slice 9 closes the remaining caller-selected specialist gap in the generic safe-tool execution path.

The safe-tool registry already enforced exact tool-family authorization before owner policy, but `ToolExecutionService` previously accepted `taskId`, `agentId` and `OperationMode` as independent request fields. A caller could therefore name a specialist that owned a registered tool even when that specialist was not the durable task's current execution specialist. The same request-supplied mode was also used for owner-policy evaluation without first proving it matched the task's stored mode.

Slice 9 does not add a tool, endpoint, agent, credential, shell primitive or external write capability. It makes existing safe-tool adapters consume durable task authority while preserving the existing engineering QA lifecycle.

## Execution-time boundary

Before owner policy, owner approval, or a tool adapter can execute, `ToolExecutionService` now delegates to `ToolExecutionAuthorityService`, which:

1. requires a durable task context;
2. requires the request `agentId` to exactly equal the task's current `activeAgentId`;
3. requires that specialist to still own the exact mapped tool family in the agent catalog;
4. when the caller supplies a mode, requires it to exactly match the task's durable mode;
5. permits **read-only** registered tools only while the task is `RUNNING` or `VERIFYING`;
6. permits **mutating** registered tools only while the task is `RUNNING`.

`ToolExecutionService` preserves emergency-stop precedence, then applies the authority result, evaluates ordinary owner policy using the **durable task mode**, checks any required exact owner approval, and only then invokes filesystem, terminal or GitHub adapters.

A missing task, reassigned task, invalid state, tool-family mismatch or mode mismatch returns a fail-closed `BLOCKED` execution result and no tool adapter is invoked.

### Verification-state compatibility

The `VERIFYING` exception is deliberately limited to tools whose registered `ToolDescriptor.readOnly()` value is `true`. This preserves the established Engineering -> QA -> independent-verification lifecycle, where `qa-engineer` legitimately runs `terminal.inspect` while the task is `VERIFYING`.

A mutating tool such as `terminal.execute-workspace`, `files.write-workspace` or `github.propose-change` remains ineligible in `VERIFYING` even when the active verifier owns its tool family. Owner approval cannot expand that state boundary.

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
- denied owner approval still prevents the mutation adapter from running;
- a read-only QA inspection tool can run in `VERIFYING` under the exact active specialist;
- a mutating terminal tool remains blocked in `VERIFYING` even when that specialist owns the terminal family.

The Phase 12 workflow additionally verifies source order:

`task-specialist/state authority -> durable-mode policy -> owner approval -> tool adapter`

and statically asserts that read-only states are exactly `RUNNING + VERIFYING` while mutating states remain exactly `RUNNING`.

## Contract and truth boundaries

No HTTP endpoint, JPA table/field, agent ID, credential, tool descriptor or external adapter is added. No physical-PC/browser validation is claimed. The Stage 23 compatibility surface should remain unchanged.

## Rollback

Revert `ToolExecutionAuthorityService`, the `ToolExecutionService` authority/mode binding, `Phase12SafeToolExecutionAuthorityTest`, the Phase 12 workflow additions and this document as one unit.

## Remaining Phase 12 audit

After this slice, the remaining specialist-governance audit should focus on identity continuity rather than caller-selected execution identity. In particular, Slice 8 documented that OAuth access-token references legitimately rotate during refresh, while the current connector credential model does not yet prove stable provider-account subject continuity across a full reauthorization. Any follow-up must use a durable non-secret provider identity/generation rather than freezing access-token references.
