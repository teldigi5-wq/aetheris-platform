# Phase 12 Slice 7 — GitHub Publish Authority

Phase 12 Slice 7 closes a delayed-approval authority gap in the guarded GitHub proposal workflow.

A GitHub change proposal is intentionally inert: it records proposed content and can wait for owner review. Before this slice, however, a later `publish(...)` call checked the exact proposal approval and GitHub credential/repository controls without revalidating that the proposal's original specialist was still authorized to execute the parent task. A proposal could therefore outlive the task assignment that originally justified it.

Slice 7 binds the actual GitHub write to current durable specialist authority at publish time.

## Publish-time authority boundary

`GitHubProposalPublishService.publish(...)` now requires, before checking the stored approval and before calling `GitHubAdapterService.publishExistingFile(...)`:

1. the proposal is bound to a durable task;
2. that task is still `RUNNING`;
3. the proposal `agentId` is a known catalog specialist;
4. the proposal `agentId` exactly matches the task's current `activeAgentId`;
5. the active specialist still owns the exact `github` tool family.

The check reuses `DirectExecutionAuthorityService`, so the GitHub publish path follows the same fail-closed identity and tool-family model as MCP, real host commands and physically enabled generic-browser execution.

## Delayed approval semantics

Proposal creation and approval request remain non-effectful and unchanged. The proposal may wait for an owner decision.

At the actual effect boundary, the required order is:

1. emergency-stop check;
2. current task-specialist GitHub authority;
3. exact owner approval for `github.publish:<proposalId>`;
4. guarded GitHub adapter execution.

An old approval therefore cannot authorize a write after the task has been paused/reassigned or after the specialist loses GitHub authority. Owner approval remains mandatory, but approval cannot create specialist authority.

## Existing GitHub safeguards preserved

The existing adapter controls remain intact:

- repository allowlist;
- owner/name repository validation;
- guarded existing-file update using the current file SHA;
- branch/ref and path handling;
- scoped credential access for `GITHUB_READ` and `GITHUB_PUBLISH`;
- credential redaction and char-array zeroization;
- invocation audit;
- emergency stop;
- exact proposal approval.

Slice 7 does not add a new credential, endpoint, repository target, write primitive or bypass around those controls.

## Acceptance proof

`Phase12GitHubPublishAuthorityTest` proves:

- an approved proposal publishes only after current specialist authority succeeds;
- the authority check occurs before approval consumption and before the GitHub adapter effect;
- a stale approval cannot override a reassigned/invalid task specialist;
- authority denial never reaches `GitHubAdapterService`;
- valid specialist authority does not replace the exact proposal owner approval.

The Phase 12 workflow also statically verifies the production source ordering:

`directAuthority.requireRunningSpecialist(...)` → `approvals.hasApproved(...)` → `github.publishExistingFile(...)`.

## Contract impact

No new HTTP endpoint, JPA table, JPA field, agent ID, credential, tool family, request field or GitHub operation is added. The change is an internal authorization check at an existing effect boundary, so the Stage 23 contract should remain unchanged.

## Rollback

Revert the `GitHubProposalPublishService` authority injection/check, `Phase12GitHubPublishAuthorityTest`, the Phase 12 workflow additions and this document as one unit.

## Remaining Phase 12 audit

Slice 7 does not by itself declare Phase 12 complete. Remaining direct or durable effect services should still be audited for the same invariant: a consequential effect must consume current specialist authority, applicable owner policy/approval and later independent task verification rather than trusting stale caller-supplied identity.
