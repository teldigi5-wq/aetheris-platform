# Stage 3 — Policy Compilation, MCP Governance and Multi-Agent Workflows

Stage 3 moves Syntra/Aetheris from a durable control plane to an enforceable orchestration layer. Owner Rules now participate in actual action decisions, approvals control task execution, task events survive reconnects, MCP integrations are governed through explicit grants, model routing refuses to invent unavailable providers, and the first multi-agent software workflow is executable as a real state machine.

## 1. Compiled Owner Rules

The base policy engine remains non-bypassable and is evaluated before editable Owner Rules. Active rules can then strengthen a decision through:

- `DENY`
- `REQUIRE_APPROVAL`
- `FORCE_LOCAL`
- `FORCE_ZERO_COST`
- `ALLOW`

A compact deterministic condition grammar supports clauses such as:

```text
action=tool:github.propose-change
scope=tool && risk=HIGH
mode=ZERO_COST;billable=true
metadata.toolId=terminal.execute-workspace
```

The compiler supports exact values, comma-separated alternatives, `!=`, wildcard `*`, and selected action metadata. Owner Rules cannot weaken a hard denial from the base policy or remove a base HIGH/CRITICAL approval requirement.

Endpoint:

- `POST /api/orchestrator/policy/compile`

The decision reports whether the action is allowed, whether approval is required, whether execution must stay local/zero-cost, the reasons, and the matched Owner Rule keys.

## 2. Approval-driven task execution

The approval queue is now coupled to the task state machine.

When a guarded workflow requests approval:

```text
PLANNING -> AWAITING_APPROVAL
```

When the last required approval is granted:

```text
AWAITING_APPROVAL -> RUNNING
```

When a required approval is rejected:

```text
AWAITING_APPROVAL -> CANCELLED
```

Multiple pending approvals are supported. A task resumes only when no required approval remains pending.

## 3. Persistent task-event journal and replay

Task events are now written to `aetheris_task_events` before live publication.

The live SSE endpoint replays durable history and then sends the current snapshot, allowing a future Syntra desktop/mobile interface to reconnect after a refresh or service interruption without losing the visible task timeline.

Endpoints:

- `GET /api/orchestrator/tasks/{id}/events/history`
- `GET /api/orchestrator/tasks/{id}/events`

The task service can also record same-state progress events, allowing agents to report meaningful handoffs without inventing illegal state transitions.

## 4. MCP registry and capability grants

Stage 3 introduces a persistent MCP registry.

Each MCP server records:

- stable server key and display name
- endpoint
- local/remote classification
- enabled state
- approved capabilities
- allowed data classes
- health status and last health timestamp

Health states are:

- `UNKNOWN`
- `HEALTHY`
- `UNHEALTHY`
- `DISABLED`

Capabilities are not automatically inherited by every agent. An explicit per-agent capability grant is required. Grant creation validates that:

1. the agent exists in the Aetheris agent catalog;
2. the MCP server is enabled;
3. the requested capability is approved for that server; and
4. the requested data class is permitted.

Endpoints include:

- `POST /api/orchestrator/mcp/servers`
- `GET /api/orchestrator/mcp/servers`
- `POST /api/orchestrator/mcp/servers/{serverId}/health`
- `POST /api/orchestrator/mcp/servers/{serverId}/grants`
- `GET /api/orchestrator/mcp/servers/{serverId}/grants`
- `POST /api/orchestrator/mcp/grants/{grantId}/revoke`

MCP remains an integration protocol, not a way to bypass provider billing, account restrictions or platform permissions.

## 5. Policy-gated tool catalog

The first safe tool catalog defines capability contracts for:

- `github.read`
- `github.propose-change`
- `files.read-workspace`
- `files.write-workspace`
- `terminal.inspect`
- `terminal.execute-workspace`

These are capability descriptions and policy gates, not unrestricted shell access. Every access evaluation verifies the agent and routes the proposed operation through the compiled Owner Rule policy.

Endpoints:

- `GET /api/orchestrator/tools`
- `POST /api/orchestrator/tools/evaluate`

The design intentionally separates **permission to use a tool** from **the adapter that performs the action**. Real PC/filesystem/terminal execution remains disabled until the relevant host adapter and sandbox exist.

## 6. Model router v1

The first model router knows about the configured local Ollama provider and refuses to invent cloud availability.

Routing behavior:

- if the local model is healthy, it is selected as the current zero-cost local provider;
- `PRIVATE` or protected-data work does not fall back off-device when local AI is unavailable;
- `ZERO_COST` does not silently use paid inference;
- paid/cloud routing remains unavailable until an owner-approved provider adapter is explicitly configured.

Endpoints:

- `POST /api/orchestrator/models/route`
- `GET /api/orchestrator/models/providers`

This is the foundation for later provider health scoring, free/student quota tracking, model benchmarking and task-specific routing.

## 7. First multi-agent engineering workflow

Stage 3 adds the first executable multi-agent workflow:

```text
Executive Planner
      |
      v
Backend Engineer
      |
      v
QA Engineer
      |
      v
Software Architect (independent verifier)
      |
      v
Completed
```

Workflow phases:

- `AWAITING_APPROVAL`
- `ENGINEERING`
- `QA`
- `VERIFICATION`
- `COMPLETED`
- `CANCELLED`

The workflow uses the real task state machine, policy compiler, approval queue and task-event journal. It does **not** fabricate code changes, test evidence or model output. At this stage it orchestrates verified phases and handoffs; later stages will connect each phase to real model/tool adapters.

Endpoints:

- `POST /api/orchestrator/workflows/engineering`
- `GET /api/orchestrator/workflows/engineering`
- `GET /api/orchestrator/workflows/engineering/{id}`
- `POST /api/orchestrator/workflows/engineering/{id}/advance`

## 8. Verification

GitHub Actions run 236 validated the Stage 3 code after a compiler issue in the new tool registry was detected and repaired.

The expanded test suite verifies:

- Owner Rule compilation can block policy-gated tools;
- approvals move tasks into and out of `AWAITING_APPROVAL`;
- task-event history is persisted and replayable;
- MCP capability grants reject unapproved capabilities;
- Zero-Cost routing does not invent a cloud provider when local AI is offline; and
- Engineering -> QA -> independent verifier workflow reaches `COMPLETED` through the legal task states.

## Next — Stage 4

Stage 4 should connect the control plane to real, still-safe execution primitives:

1. provider adapter SPI and free/local provider health scoring;
2. MCP client connection manager with protocol-level discovery;
3. GitHub read/propose-change adapter behind owner policy;
4. workspace filesystem sandbox with path allowlists;
5. command sandbox with allowlists, timeouts and captured output;
6. workflow executor that invokes adapters instead of manual phase advancement;
7. verifier evidence model and acceptance criteria;
8. structured audit trail for every model/tool invocation;
9. task cancellation propagation and emergency-stop controls; and
10. initial Syntra operations dashboard consuming task/event/approval/model APIs.
