# Stage 2 — Durable Syntra/Aetheris Control Plane

Stage 2 turns the initial agent catalog into a durable, observable control plane that can survive UI refreshes and future service restarts.

## Implemented

### Persistent task lifecycle

Tasks are stored in PostgreSQL in normal runtime and H2 during tests. A task records its owner command, operation mode, active agent, state, timestamps and optimistic-lock version.

Supported lifecycle states:

`QUEUED -> PLANNING -> AWAITING_APPROVAL -> RUNNING -> VERIFYING -> COMPLETED`

Recovery states include `PAUSED`, `FAILED`, `ROLLING_BACK` and `CANCELLED`. The service rejects invalid state jumps.

### Live task events

`GET /api/orchestrator/tasks/{id}/events` exposes Server-Sent Events (SSE). The future Syntra desktop UI can render real progress from backend task events rather than fake loading animations or repeated polling.

### Owner approval queue

High-impact workflows can create durable approval records with task ID, action type, summary and risk level.

Endpoints:

- `POST /api/orchestrator/approvals`
- `GET /api/orchestrator/approvals/pending`
- `GET /api/orchestrator/approvals/task/{taskId}`
- `POST /api/orchestrator/approvals/{id}/decision`

This queue is intended for public-profile changes, GitHub publishing, driver/system changes, sensitive cybersecurity actions, trading execution, purchases and other externally consequential operations.

### Versioned Owner Rules

Owner rules are stored as immutable revisions. Creating a new revision deactivates the previous revision for the same key while preserving history.

Rule effects currently modeled:

- `ALLOW`
- `DENY`
- `REQUIRE_APPROVAL`
- `FORCE_LOCAL`
- `FORCE_ZERO_COST`

Endpoints:

- `POST /api/orchestrator/rules/revisions`
- `GET /api/orchestrator/rules/active`
- `GET /api/orchestrator/rules/{ruleKey}/history`

The next policy-engine stage will compile these structured rules into deterministic decisions before tool execution.

### Local model adapter

A first replaceable Ollama adapter now exposes:

- `GET /api/orchestrator/models/local/health`
- `POST /api/orchestrator/models/local/generate`

The adapter is configuration-driven and reports unavailable cleanly when no local model server exists. This allows cloud/GitHub development to continue before the target Windows/RTX machine is available.

Runtime variables:

- `AETHERIS_LOCAL_MODEL_BASE_URL`
- `AETHERIS_LOCAL_MODEL`

Docker defaults to `http://host.docker.internal:11434`, suitable for the planned Windows-first workstation once Ollama is installed.

## Persistence

Runtime defaults use the platform PostgreSQL database. Tests use isolated in-memory H2 in PostgreSQL compatibility mode.

The development Compose override injects the orchestrator database connection without replacing the existing Stage-7 Compose definition.

## Verification

`ControlPlaneIntegrationTest` verifies:

- task persistence and state transition
- approval creation and owner decision
- rule revision history and active-version selection
- graceful local-model unavailability when no PC endpoint is present

## Next implementation slice

1. policy compiler that evaluates active Owner Rules against proposed actions
2. approval-to-task resume workflow
3. persistent task-event journal and replay after restart
4. MCP server registry + connection health + scoped capability grants
5. safe GitHub/files/terminal tool adapters
6. first Engineering -> QA -> Verifier multi-agent workflow
7. model router with local/free/provider health scoring
8. Syntra desktop live-operations shell consuming the SSE stream
