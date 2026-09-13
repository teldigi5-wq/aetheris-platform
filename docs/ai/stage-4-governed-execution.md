# Stage 4 — Governed Execution, Evidence and Syntra Operations

Stage 4 connects the Stage 3 control plane to real execution primitives while keeping owner policy, approvals, audit, sandbox boundaries and emergency-stop controls in the execution path.

## Provider adapter SPI

Model routing now uses a provider adapter interface instead of being hard-wired directly to Ollama. The first adapter is `OllamaModelProviderAdapter`, and `ModelProviderRegistry` selects only healthy providers that satisfy hard locality/cost constraints.

Private or protected-data work requires a local provider. Zero-Cost work requires a zero-cost provider. Unavailable providers are never fabricated, and paid/cloud providers remain disabled until an owner-approved adapter exists.

`ModelExecutionService` invokes the selected provider and writes a durable invocation audit record.

Endpoints:

- `GET /api/orchestrator/models/providers`
- `POST /api/orchestrator/models/route`
- `POST /api/orchestrator/models/execute`

## MCP 2026-07-28 connection probing

The MCP connection manager targets protocol revision `2026-07-28`, using stateless request metadata, `MCP-Protocol-Version`, `Mcp-Method`, `server/discover` and `tools/list`.

Endpoint safety is fail-closed:

- local MCP endpoints must use HTTP/HTTPS and an allowlisted local host;
- remote MCP endpoints must use HTTPS;
- emergency stop blocks discovery calls;
- failed discovery marks the registered server unhealthy; and
- discovery attempts are audited.

Endpoint:

- `POST /api/orchestrator/mcp/servers/{serverId}/discover`

## Structured invocation audit

A persistent invocation journal records model, tool and MCP operations with task/agent identity, target, status, metadata, timestamps and result detail.

Statuses:

- `STARTED`
- `SUCCEEDED`
- `FAILED`
- `BLOCKED`
- `CANCELLED`

Endpoints:

- `GET /api/orchestrator/invocations`
- `GET /api/orchestrator/invocations/task/{taskId}`

## Emergency stop and cancellation propagation

The emergency stop is a deterministic local control-plane primitive and does not depend on an LLM response.

When engaged it marks the stop active, cancels currently active tasks where legal, and blocks new model/MCP/tool execution.

Controls:

- `GET /api/orchestrator/control/emergency-stop`
- `POST /api/orchestrator/control/emergency-stop/engage`
- `POST /api/orchestrator/control/emergency-stop/release`
- `POST /api/orchestrator/control/tasks/{taskId}/cancel`

The task state machine now supports cancellation during verification as well as earlier active states.

## Workspace filesystem sandbox

`WorkspaceSandboxService` constrains file operations to one configured root.

Security properties:

- absolute paths are rejected;
- normalized paths must remain below the sandbox root;
- traversal outside the root is rejected;
- file size is bounded;
- writes are disabled by default; and
- enabling writes is an explicit runtime configuration decision.

Runtime settings:

- `AETHERIS_WORKSPACE_ROOT`
- `AETHERIS_WORKSPACE_WRITE_ENABLED`
- `AETHERIS_MAX_FILE_BYTES`

## Command sandbox

`CommandSandboxService` executes argument arrays directly through `ProcessBuilder`; it does not invoke a shell.

Controls include an executable allowlist, sandboxed working directory, bounded timeout, bounded captured output, and forced process termination on timeout.

Default command families:

- `git`
- `mvn`
- `npm`
- `node`
- `java`
- `javac`

The allowlist is configurable with `AETHERIS_ALLOWED_COMMANDS`.

## GitHub read/propose-change adapter

`github.read` can read an allowlisted repository file through the GitHub API.

`github.propose-change` deliberately does **not** push directly. It creates a durable local proposal containing repository, path, base ref, proposed content, task/agent identity and summary. Direct publishing remains a later separately approved capability.

Runtime settings:

- `AETHERIS_GITHUB_ALLOWED_REPOSITORIES`
- `AETHERIS_GITHUB_TOKEN`

Repository allowlisting is mandatory.

## Policy-gated tool execution

`ToolExecutionService` connects the Stage 3 tool catalog to real adapters. Before execution it verifies task existence when supplied, agent/tool registration, base policy, Owner Rules, emergency-stop state, and required approval evidence for high-impact actions.

Supported Stage 4 execution adapters:

- `files.read-workspace`
- `files.write-workspace`
- `terminal.inspect`
- `terminal.execute-workspace`
- `github.read`
- `github.propose-change`

Endpoint:

- `POST /api/orchestrator/tool-executions`

## Evidence-driven Engineering -> QA -> Verifier execution

The engineering workflow can now execute real safe adapters instead of only moving phases manually.

```text
ENGINEERING
  -> read a real workspace file through the filesystem sandbox
  -> record SOURCE_READ evidence

QA
  -> run an allowlisted command through the command sandbox
  -> record QA_COMMAND evidence including exit status/output

VERIFICATION
  -> Software Architect checks deterministic acceptance criteria against recorded evidence
  -> record ACCEPTANCE_CRITERIA evidence
  -> COMPLETE or FAIL
```

Supported deterministic acceptance criteria currently include:

- `source-readable`
- `qa-command-passed`

Unknown criteria fail closed until a verifier plugin is implemented.

Endpoint:

- `POST /api/orchestrator/workflows/engineering/{id}/execute-next`

The original manual phase endpoint remains available for control-plane debugging.

## Gateway scopes and Syntra operations dashboard

The gateway now exposes `/api/orchestrator/**` through dedicated JWT scopes:

- `orchestrator:read`
- `orchestrator:write`

Default role mapping:

- `API_CONSUMER`: read
- `DEVELOPER`: read + write
- `ADMIN`: read + write

The dashboard now consumes orchestrator APIs and presents recent AI tasks, pending approvals, provider health, invocation audit entries and emergency-stop state. Owner emergency-stop control is shown only when `orchestrator:write` is present and requires explicit browser confirmation.

## Verification

GitHub Actions run 239 validated the Stage 4 backend execution layer.

GitHub Actions run 240 validated the complete Stage 4 head after gateway scopes and the Syntra operations dashboard were added.

The Stage 4 tests verify:

- workspace traversal is rejected;
- sandbox reads work;
- non-allowlisted shell commands are rejected before process start;
- real tool execution is audited;
- emergency stop blocks new tool execution;
- the provider SPI reports the configured offline local provider without inventing health;
- unsafe remote HTTP MCP endpoints are rejected before any network call; and
- Engineering -> QA -> Verifier completes using real sandbox file evidence and an allowlisted `java -version` QA command.

## Next — Stage 5

Stage 5 should move from safe single-host primitives toward a production-grade personal AI workstation:

1. OS-backed credentials vault abstraction;
2. approved cloud/free-provider adapters with quota/cost accounting;
3. real MCP tool invocation with grant enforcement and schema validation;
4. GitHub proposal diff review plus owner-approved publish path;
5. durable workflow queue, retries, checkpoints and resumable execution;
6. Windows host daemon for app/process/PC telemetry operations;
7. workspace checkpoint/rollback and Git branch isolation before writes;
8. SSE/WebSocket Syntra live operations stream for interruption and progress;
9. task pause/resume/take-control propagation into active adapters; and
10. first desktop Syntra shell consuming the governed Aetheris APIs.
