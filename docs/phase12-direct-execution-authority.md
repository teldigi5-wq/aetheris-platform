# Phase 12 Slice 5 — Direct Execution Authority

Phase 12 Slice 5 closes concrete authority gaps found while auditing execution paths that can bypass the durable work queue and scheduler.

The audit found two effect boundaries that were already protected by substantial server/grant, host, signing, audit and emergency controls, but were not bound to the specialist currently executing the parent task:

- MCP `tools/call` accepted a catalog `agentId` and optional task ID independently of the task's active specialist.
- non-simulation host command issuance carried host/capability data but no task or specialist authority context.

Slice 5 does not add a new persona, remote transport, credential, table, live-money path or physical-hardware claim. It makes existing direct-effect paths consume the same specialist authority model established by earlier Phase 12 slices.

## Shared direct-execution boundary

`DirectExecutionAuthorityService` is the reusable fail-closed check for an execution path that does not obtain authority through a specialist-bound worker lease.

A real direct effect is authorized only when:

1. a task ID is present and resolves to a durable task;
2. the task is `RUNNING`;
3. the claimed agent is a known catalog specialist;
4. the claimed agent exactly matches the task's current `activeAgentId`;
5. the specialist's `allowed-tools` contains the exact required direct tool family;
6. when a caller supplies an operation mode, it exactly matches the task's stored `OperationMode`.

There is no wildcard tool or identity grant. Owner approval cannot substitute for the task/specialist identity check.

## MCP `tools/call`

MCP discovery, server health and capability configuration remain separate from execution. Before the first `tools/list`/`tools/call` network request for an invocation, `McpToolInvocationService` now requires the active task specialist to own the exact `mcp` tool family.

Existing MCP server controls remain in force:

- server enabled and healthy;
- capability approved by the server registration;
- data class allowlisted;
- active server/agent/capability/data-class grant;
- local-host allowlist for local MCP servers;
- HTTPS for remote MCP servers;
- emergency stop;
- invocation audit.

In addition, a `PRIVATE` task cannot execute against a remote MCP server. The task's durable mode, rather than caller-selected execution context, therefore prevents off-device MCP execution.

The authority check intentionally occurs after cheap deterministic grant checks but before any network request. Existing requests that would already be rejected for missing MCP grants retain their original fail-closed reason without making a network call.

## Host command issuance

`HostCommandRequest` now carries optional `taskId` and `agentId` authority context while retaining the legacy four-argument Java constructor for simulation fixtures.

Simulation behavior remains unchanged because it does not execute an operating-system action. When `aetheris.host.simulation-only=false`, however, `HostCommandService.issue(...)` now requires both:

- the active task specialist to own `pc-control`; and
- explicit owner approval for `host:command` on that task.

Existing host controls still apply first or in addition: the host must be paired/online and executable, the requested host capability must be declared, and the command signing key must be configured before a signed envelope can be emitted.

This is repository-side authorization hardening only. It does **not** claim that a physical Windows host, remote transport or hardware-backed identity has been validated. The repository's hardware truth boundary remains unchanged.

## Verification and completion

Slice 5 does not create an alternate completion path. Tasks still pass through the Phase 12 Slice 2 `VERIFYING -> COMPLETED` boundary, so an executor cannot self-certify final success. Direct invocation audit/evidence is execution evidence; it is not a replacement for independent task verification.

## Acceptance proof

`Phase12DirectExecutionAuthorityTest` proves:

- correct RUNNING active-specialist execution is accepted;
- a different catalog specialist cannot borrow the task;
- an active specialist cannot use a direct tool family absent from its catalog grant;
- PAUSED/non-running tasks cannot directly execute;
- supplied operation mode cannot differ from the task's durable mode;
- real host command issuance consumes specialist authority and explicit owner approval;
- simulation-only host command proofs do not pretend to be live authority evidence.

The Phase 12 workflow also statically verifies that MCP and non-simulation host issuance call the shared authority boundary and that host requests carry task/agent context.

## Contract impact

No new HTTP endpoint, JPA table, agent ID, credential or execution adapter is added.

`HostCommandRequest` additively exposes nullable `taskId` and `agentId` fields. The existing Java four-argument constructor remains available for simulation-only callers. A real `REMOTE` host envelope now fails closed unless the new authority context and owner approval are present.

MCP request shape is unchanged; its existing nullable `taskId` becomes mandatory at the actual effect boundary. Discovery/health behavior is unchanged.

## Rollback

Revert `DirectExecutionAuthorityService`, the MCP authority/mode checks, the host request authority fields, the non-simulation host authority/approval gate, Slice 5 tests/workflow guards and this document as one unit.

## Remaining Phase 12 audit

Slice 5 does **not** close Phase 12. The next direct-effect audit target is the generic browser operator when both `runtime-enabled=true` and `physical-validated=true`. That path already has domain, risk, owner-policy, approval, emergency, download-evidence and live-money controls, but must still be proven to bind its claimed `agentId` and supplied mode to the durable task's active specialist before the WebDriver adapter can execute.

Other direct-effect surfaces should continue to be checked for the same invariant before Phase 12 is declared complete.
