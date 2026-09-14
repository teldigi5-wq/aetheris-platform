# Stage 6 — Resilient Workstation Fabric

Stage 6 extends Syntra + Aetheris from a governed single-host runtime into a hardware-safe workstation fabric with cryptographic host trust, durable worker leases, isolated Git write boundaries, model-provider resilience, mission sessions and a richer owner mission-control surface.

The target Windows PC is not required for this stage. Real operating-system execution remains disabled by default and the host path is simulation-first.

## 1. Cryptographic host pairing

Hosts still begin in `UNPAIRED` state and cannot execute commands.

Pairing now uses a challenge/response proof-of-possession flow:

1. A host is registered with a declared public-key fingerprint and capability set.
2. Aetheris creates a random 32-byte nonce with a five-minute expiry.
3. Only the SHA-256 hash of the nonce is persisted.
4. The host signs `hostId:challengeId:nonce` using its private key.
5. Aetheris validates the supplied public key against the registered SHA-256 fingerprint.
6. Aetheris verifies the signature using RSA/SHA-256 or ECDSA/SHA-256.
7. The challenge is consumed once and the host becomes `OFFLINE` but paired.
8. An authenticated heartbeat moves it to `ONLINE`.

Revocation marks the pairing credential and host as revoked. Revoked or unpaired hosts cannot execute or report trusted telemetry.

Endpoints include:

- `POST /api/orchestrator/hosts/{id}/pairing-challenges`
- `POST /api/orchestrator/hosts/{id}/pairing-challenges/{challengeId}/complete`
- `POST /api/orchestrator/hosts/{id}/heartbeat`
- `POST /api/orchestrator/hosts/{id}/revoke`

## 2. Signed host command envelopes — simulation first

Aetheris can issue short-lived host command envelopes only for a paired, online host and only for a capability the host declared.

Envelopes include:

- command id;
- host id;
- capability;
- action;
- arguments;
- issue and expiry timestamps;
- HMAC-SHA256 signature; and
- execution mode.

The default execution mode is `SIMULATION`. The simulation verifier checks host trust, envelope expiry and signature integrity and then returns a simulated result without executing an OS action.

Runtime configuration:

- `AETHERIS_HOST_CONTROL_SIGNING_KEY`
- `AETHERIS_HOST_SIMULATION_ONLY=true`

Stage 6 deliberately does not install drivers, launch applications, change Windows settings, update drivers, invoke PowerShell, or execute real host commands.

## 3. Durable worker leases and abandoned-work recovery

Stage 5's persistent work items now support explicit worker leases.

A claim records:

- worker id;
- lease start;
- lease expiry; and
- attempt number.

Workers can renew their leases. If a `RUNNING` work item is abandoned and its lease expires, the recovery path moves it back to `RETRY_WAIT` when retries remain or to `FAILED` when the attempt limit is exhausted.

A recovered lease is never interpreted as success.

Endpoints include:

- `POST /api/orchestrator/work-queue/{id}/claim`
- `POST /api/orchestrator/work-queue/{id}/renew-lease`
- `POST /api/orchestrator/work-queue/recover-abandoned`

## 4. Git workspace isolation before agent writes

Before an agent-controlled write workflow begins, Aetheris can isolate the workspace on a task-specific Git branch.

Safety rules:

- the configured workspace must be a Git work tree;
- the workspace must be clean before isolation;
- Aetheris refuses to hide or overwrite existing owner changes;
- the isolated branch is named `aetheris/task-*`;
- the original HEAD commit is persisted as a checkpoint before writes.

Endpoint:

- `POST /api/orchestrator/workspace/git/tasks/{taskId}/isolate`

## 5. Separately approved rollback

Rollback is intentionally distinct from checkpoint creation.

A rollback request creates a `HIGH` risk owner approval tied to the exact checkpoint id. Execution is allowed only when:

- the checkpoint is a `GIT_BRANCH` checkpoint;
- that exact rollback action was approved; and
- the current branch is an isolated `aetheris/task-*` branch.

Only then may Aetheris perform the checkpoint restore.

Endpoints:

- `POST /api/orchestrator/workspace/git/checkpoints/{checkpointId}/request-rollback`
- `POST /api/orchestrator/workspace/git/checkpoints/{checkpointId}/rollback`

## 6. Provider circuit breakers and reliability scoring

Provider selection now considers durable reliability evidence in addition to locality, cost and operation mode.

Aetheris records per-provider:

- successes;
- failures;
- consecutive failures;
- total/average runtime latency;
- last error; and
- circuit-open expiry.

The default circuit opens after three consecutive runtime failures and cools down for 60 seconds. An open provider is excluded from new routing attempts until its cooldown expires.

Private/protected-data and Zero-Cost constraints remain hard filters. A fallback provider cannot bypass them.

Runtime configuration:

- `AETHERIS_PROVIDER_CIRCUIT_FAILURE_THRESHOLD`
- `AETHERIS_PROVIDER_CIRCUIT_COOLDOWN_SECONDS`

## 7. Model fallback chain and Model Arena evidence

A model execution now builds an ordered list of eligible providers and can move to the next provider when an earlier eligible provider fails.

Before each attempt Aetheris still checks quota and budget policy. Runtime results are written to both the provider reliability record and the Model Arena measurement store.

Model Arena measurements contain:

- provider;
- model;
- scenario;
- success/failure;
- latency;
- optional quality score; and
- detail.

Endpoints:

- `GET /api/orchestrator/models/provider-health`
- `GET /api/orchestrator/models/arena`
- `POST /api/orchestrator/models/arena`
- `GET /api/orchestrator/models/budgets`

## 8. Syntra mission sessions

Stage 6 introduces durable mission sessions so Syntra can group a conversation and multiple Aetheris tasks under one objective.

A mission stores:

- title and objective;
- `ACTIVE`, `PAUSED`, `COMPLETED` or `CANCELLED` state;
- linked task ids;
- owner/Syntra/system messages; and
- a reference to the global live operations stream.

Mission views use the existing aggregate stream:

- `/api/orchestrator/live/events`

The mission API can also aggregate durable event history from every attached task.

Endpoints:

- `POST /api/orchestrator/missions`
- `GET /api/orchestrator/missions`
- `POST /api/orchestrator/missions/{id}/tasks/{taskId}`
- `POST /api/orchestrator/missions/{id}/status/{status}`
- `POST /api/orchestrator/missions/{id}/messages`
- `GET /api/orchestrator/missions/{id}/messages`
- `GET /api/orchestrator/missions/{id}/task-events`

## 9. Stage 6 mission-control dashboard

The dashboard has been promoted from the initial Stage 4 operations view to a Stage 6 mission-control surface.

It now displays and controls:

- recent tasks with pause, resume, take-control and cancel actions;
- durable work queue and expired-lease recovery;
- model provider availability and circuit state;
- provider quota/cost guards;
- Syntra mission sessions;
- trusted host pairing/online state;
- GitHub proposals and owner-approved publish path;
- MCP server trust state and per-agent capability grants;
- pending approvals;
- invocation audit; and
- the deterministic emergency stop.

A visible hardware-safe banner states that Windows host commands remain simulation-only.

## 10. Desktop shell contract

`apps/syntra-desktop` now defines the first desktop-shell contract without pretending that a Windows executable has already been built.

The future desktop client must:

- consume the authenticated Aetheris APIs;
- subscribe to the global mission stream;
- expose deterministic `STOP`, `PAUSE`, `RESUME`, `TAKE_CONTROL`, `APPROVE` and `REJECT` controls;
- pair to the Windows host daemon through challenge/response;
- use OS-backed secret storage in production;
- show task/model/provider/tool/approval/quota/host/checkpoint state;
- protect interaction responsiveness before decorative GPU effects; and
- keep high-impact PC actions behind owner policy and approval.

Real `.exe` packaging, Windows service installation, driver access, real hardware telemetry and GPU tuning are deferred until the target PC is available.

## Verification

GitHub Actions run 255 caught a compile-time missing import in the new host-envelope verifier before Stage 6 could be marked complete. The defect was repaired without bypassing CI.

GitHub Actions run 257 validated the complete Stage 6 code head after the repair and mission-control dashboard integration.

Run 257 passed:

- dashboard build;
- gateway tests;
- user-service tests;
- identity-service tests;
- audit-service tests; and
- expanded orchestrator tests.

The Stage 6 integration suite verifies cryptographic RSA pairing, host state transitions, signed simulated command verification, revocation blocking execution, abandoned-worker lease recovery, circuit opening after repeated provider failures, Model Arena evidence, mission/task linkage and fail-closed rollback without exact owner approval.

## Safety boundaries retained

- Real Windows execution remains disabled by default.
- Host pairing is not sufficient by itself to enable production PC actions; the production host executor still does not exist in Stage 6.
- Private/protected-data routing cannot silently fall back to a remote provider.
- Zero-Cost mode cannot silently fall back to a paid provider.
- Budget and quota checks remain in the provider attempt path.
- Git isolation refuses dirty owner workspaces.
- Rollback requires exact owner approval and an isolated task branch.
- MCP remains grant/schema governed.
- GitHub publication remains proposal-specific and approval-gated.
- Emergency stop remains deterministic and does not depend on model inference.
- Offensive security remains limited to owner-controlled/explicitly authorized targets or isolated labs.
- Live financial execution remains behind deterministic risk and approval controls.

## Next — Stage 7

Stage 7 should turn the Stage 6 control plane into a richer personal-AI operating environment while preserving the hardware-safe boundary:

1. durable scheduler/dispatcher service with worker heartbeats, concurrency limits and priority queues;
2. richer model router policy with per-task latency/quality/cost objectives and Model Arena automatic scoring;
3. provider rate-limit/backpressure handling and quota-reset awareness;
4. mission planner that decomposes owner intent into dependency-aware task graphs;
5. project knowledge graph and durable semantic memory with local-first indexing;
6. proactive intelligence engine for project health, deadlines, dependency/security drift and owner-approved suggestions;
7. credential-vault provider interface for Windows Credential Manager/DPAPI implementation later;
8. host-daemon protocol package with replay protection, command acknowledgements and simulated capability handlers;
9. notification/event center for approvals, failures, opportunity alerts and critical PC/project events;
10. first Syntra voice-session protocol contracts for streaming STT/TTS, barge-in and deterministic priority commands;
11. recruiter/career intelligence pipelines for GitHub, portfolio and public-profile health;
12. trading intelligence subsystem focused first on market analysis, signal construction, TP/SL/leverage calculation and explicit owner acceptance before any execution adapter.
