# Stage 5 — Production Runtime, Credentials, Resumability and Future Host Boundary

Stage 5 moves Syntra/Aetheris closer to a production workstation runtime while remaining hardware-independent until the owner's target PC is available. The stage adds fail-closed credentials, explicit provider quota/cost accounting, governed MCP tool execution, exact-proposal GitHub publishing approval, durable retry/checkpoint primitives, aggregate live operations streaming, owner pause/resume/take-control and a non-executable future Windows host contract.

## 1. Credential vault abstraction

Secrets are referenced by alias rather than persisted in tasks, model-provider rows, GitHub proposals or audit records.

`CredentialVault` exposes only:

- alias-based resolution for an executing adapter;
- non-secret metadata describing provider and availability.

The current portable implementation reads an environment-backed secret alias and clears mutable secret buffers after use. It is intentionally an adapter boundary: a Windows Credential Manager / DPAPI implementation can be attached when the owner PC is available without changing model, GitHub or MCP workflows.

No API endpoint returns a credential value.

## 2. Cloud/free provider safeguards and accounting

A generic OpenAI-compatible cloud adapter exists but is disabled by default.

A configured cloud provider must explicitly define:

- HTTPS endpoint;
- model;
- credential alias;
- whether it is genuinely zero-cost under the configured account/provider;
- daily quota units;
- estimated cost per thousand accounting units; and
- explicit daily USD budget for paid inference.

Aetheris does not infer that a provider is free from a model name. `ZERO_COST` routing remains enforced independently by the model router.

`ProviderUsageService` records estimated usage after successful calls and blocks a provider when its configured quota or paid budget would be exceeded. Paid adapters with no positive explicit daily budget are blocked.

## 3. Governed MCP tool invocation

Stage 4 discovered MCP servers and tools. Stage 5 can invoke an advertised MCP tool only after all of these checks pass:

1. agent exists;
2. task exists when task-bound;
3. emergency stop is not active;
4. server is enabled and currently HEALTHY;
5. capability is approved for that server;
6. data class is permitted;
7. the exact agent has an active capability + data-class grant;
8. the tool is still advertised by `tools/list`;
9. required arguments are present; and
10. common JSON Schema primitive argument types match.

Remote MCP endpoints remain HTTPS-only; local MCP hosts remain allowlisted. Invocation is audited and uses the configured MCP protocol version.

Endpoint:

- `POST /api/orchestrator/mcp/tools/invoke`

## 4. Owner-approved GitHub publishing

Stage 4 stored proposed changes but could not publish them. Stage 5 adds a narrowly scoped existing-file publish path.

Flow:

```text
PROPOSE CHANGE
  -> bind proposal to task
  -> request publish approval for exact proposal UUID
  -> task moves to AWAITING_APPROVAL
  -> owner approves
  -> task resumes RUNNING
  -> publish checks exact approval again
  -> fetch current GitHub file SHA
  -> guarded contents-API update
  -> record commit SHA
```

Publishing requires:

- allowlisted repository;
- proposal status `PROPOSED`;
- task-bound proposal;
- explicit approval action `github.publish:<proposalId>`;
- GitHub credential alias available through the vault;
- existing file SHA, preventing blind overwrite of an unknown version; and
- emergency stop not active.

Stage 5 intentionally does not provide unrestricted repository writes or automatic new-file publication.

Endpoints:

- `POST /api/orchestrator/github/proposals/{id}/request-publish-approval`
- `POST /api/orchestrator/github/proposals/{id}/publish`

## 5. Durable work queue and retry model

Long-running execution is no longer modeled as an in-memory one-shot operation.

Persistent work-item states:

```text
QUEUED
  -> RUNNING
     -> SUCCEEDED
     -> RETRY_WAIT -> RUNNING
     -> FAILED
     -> PAUSED
     -> CANCELLED
```

Features:

- task-bound durable work items;
- optimistic locking through JPA versioning;
- maximum retry attempts;
- bounded exponential retry delay;
- explicit claim/succeed/fail operations;
- owner pause/resume/cancel;
- ready-work discovery; and
- emergency-stop cancellation.

The service does not pretend work continues while Aetheris is offline. A later worker/dispatcher can claim persisted ready items after restart.

## 6. Persistent execution checkpoints

`aetheris_execution_checkpoints` stores task-bound recovery markers containing:

- checkpoint type;
- label;
- external/reference identifier; and
- structured metadata JSON.

This is the durable foundation for later Git commit/branch checkpoints, deployment snapshots, migration checkpoints and PC restore metadata. Destructive rollback execution is intentionally not exposed yet; rollback will remain a separately approved operation.

## 7. Pause, resume and take control

The owner control plane now supports:

- cancel task;
- pause a RUNNING task;
- resume a PAUSED task;
- take control, which pauses the task and records an owner-control event; and
- deterministic emergency stop.

Pause/resume/cancel propagates into durable queued work for the same task. Emergency stop cancels active tasks and active durable work items without waiting for an LLM.

Endpoints include:

- `POST /api/orchestrator/control/tasks/{taskId}/pause`
- `POST /api/orchestrator/control/tasks/{taskId}/resume`
- `POST /api/orchestrator/control/tasks/{taskId}/take-control`

## 8. Aggregate live operations stream

The existing task-specific SSE stream remains available with durable history replay.

Stage 5 adds:

- `GET /api/orchestrator/live/events`

This aggregate SSE stream publishes task events across all active agents so a future Syntra desktop/mobile shell can maintain one live mission-control connection and support interruption controls.

## 9. Future Windows host boundary

Because the owner does not yet have the target PC, Stage 5 does **not** pretend Windows execution is available.

A future host can register:

- stable host key;
- display name;
- platform;
- declared capabilities such as `PROCESS_READ`, `APP_LAUNCH`, `PC_TELEMETRY` and `OLLAMA`; and
- public-key fingerprint.

Every newly registered host is `UNPAIRED`. `HostRegistryService.requireExecutable()` fails until a later authenticated pairing/heartbeat protocol moves a host into an online executable state.

This keeps PC-specific implementation replaceable and prevents development-time mocks from being mistaken for real machine control.

## 10. Validation

GitHub Actions run **250** passed:

- dashboard build;
- gateway tests;
- user-service tests;
- identity-service tests;
- audit-service tests; and
- expanded orchestrator-service tests.

Stage 5 tests verify:

- credential metadata does not expose a secret value;
- durable work moves through retry/pause/resume correctly;
- owner task controls propagate to queued work;
- a future host remains `UNPAIRED` and cannot execute;
- MCP tool calls fail closed without an exact active agent grant; and
- GitHub publication is blocked without approval for the exact proposal.

## Safety boundaries retained

- No provider is treated as free unless explicitly configured as zero-cost.
- Paid inference without an explicit positive budget is blocked.
- `PRIVATE` and `ZERO_COST` routing rules remain non-bypassable by adapters.
- Secret values are not stored in JPA entities, task events or audit output.
- MCP tool invocation requires capability/data-class grants and schema checks.
- GitHub publishing is proposal-specific and owner-approved.
- Emergency stop remains local/deterministic.
- Windows host execution remains disabled until authenticated pairing exists.
- Offensive cybersecurity actions remain restricted to owner-controlled, explicitly authorized targets or isolated labs.
- Live financial execution remains behind deterministic risk and approval controls.

## Next — Stage 6

Stage 6 should focus on a usable Syntra workstation experience while continuing to stay hardware-independent where necessary:

1. authenticated host pairing protocol with challenge/response and revocation;
2. Windows host daemon skeleton with signed commands and telemetry schemas, still runnable in simulation until the PC arrives;
3. workspace Git checkpoint + isolated branch creation before agent writes;
4. separately approved rollback executor;
5. durable worker dispatcher/lease recovery for abandoned work items;
6. provider health/circuit-breaker scoring and richer quota telemetry;
7. model fallback chains with reasoned escalation and Model Arena measurements;
8. live dashboard controls for pause/resume/take-control, work queue, MCP grants, budgets and GitHub proposals;
9. Syntra conversation/mission session model connected to aggregate SSE; and
10. desktop-shell packaging contract, with real Windows packaging/hardware tuning deferred until the target PC is available.
