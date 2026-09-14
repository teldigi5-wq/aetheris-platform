# Stage 7 — Personal Intelligence OS

Stage 7 extends Syntra + Aetheris from a resilient workstation fabric into a richer personal-AI operating environment. The system can now schedule dependency-aware work, retain local-first project knowledge, surface proactive notifications, adapt model routing to latency/quality/cost constraints, expose deterministic voice-control contracts, assess career evidence, and construct governed trading-analysis signals.

Stage 7 does **not** enable real Windows execution or live broker/exchange execution. Those remain separate future capabilities with owner-controlled security and risk gates.

## 1. Durable scheduler and backpressure

Aetheris now has a durable scheduler above the Stage 5/6 work queue.

It adds:

- persistent scheduler tickets;
- priority from 0–100;
- execution lanes;
- worker heartbeats;
- worker concurrency limits;
- highest-priority ready-work dispatch;
- configurable maximum queued tickets;
- stale-worker recovery; and
- existing durable work-item leases/retries underneath the scheduler.

The scheduler refuses additional tickets when its configured backpressure ceiling is reached rather than silently building an unbounded queue.

Core endpoints:

- `POST /api/orchestrator/scheduler/workers/heartbeat`
- `GET /api/orchestrator/scheduler/workers`
- `POST /api/orchestrator/scheduler/tickets`
- `GET /api/orchestrator/scheduler/tickets/queued`
- `POST /api/orchestrator/scheduler/workers/{workerId}/dispatch`
- `POST /api/orchestrator/scheduler/recover-stale`

## 2. Dependency-aware mission planning

Mission sessions can now own an explicit task graph.

A mission plan:

- validates each step;
- validates dependency references;
- rejects self-dependencies;
- rejects dependency cycles;
- creates durable tasks for plan steps;
- attaches tasks to the mission;
- records `BLOCKED`, `READY`, `ENQUEUED`, `COMPLETED` or `FAILED` plan-node state; and
- releases only nodes whose prerequisite task states are complete.

Released plan nodes become durable work items and scheduler tickets. A blocked node cannot run merely because a model says it is ready.

Core endpoints:

- `POST /api/orchestrator/missions/{missionId}/plan`
- `GET /api/orchestrator/missions/{missionId}/plan`
- `POST /api/orchestrator/missions/{missionId}/plan/release-ready`

## 3. Local-first knowledge graph and semantic memory

Stage 7 adds durable knowledge nodes and relation edges for:

- personal context;
- projects;
- career evidence; and
- system knowledge.

Knowledge nodes support:

- namespace + key;
- content;
- tags;
- protected-data classification; and
- local update timestamps.

Search currently uses a deterministic local lexical-semantic overlap score. This keeps Stage 7 useful without requiring a cloud embedding service or pretending an embedding index already exists.

Protected nodes are excluded from normal search unless the caller explicitly requests protected results.

Core endpoints:

- `POST /api/orchestrator/knowledge/nodes`
- `GET /api/orchestrator/knowledge/nodes`
- `POST /api/orchestrator/knowledge/edges`
- `GET /api/orchestrator/knowledge/edges`
- `GET /api/orchestrator/knowledge/search?q=...`

## 4. Proactive intelligence and notification center

Aetheris can now scan durable control-plane state and surface attention items instead of silently leaving them buried in logs.

The initial scanner detects:

- failed tasks;
- tasks awaiting owner approval;
- durable work items that exhausted retries;
- open provider circuits; and
- pending consequential approvals.

Notifications include:

- severity;
- title/message;
- source reference;
- whether owner action is required;
- unread/read/dismissed state; and
- fingerprint-based deduplication for existing unread events.

Core endpoints:

- `POST /api/orchestrator/proactive/scan`
- `GET /api/orchestrator/notifications`
- `GET /api/orchestrator/notifications/unread-count`
- `POST /api/orchestrator/notifications/{id}/read`
- `POST /api/orchestrator/notifications/{id}/dismiss`

## 5. Adaptive model objectives

Model routing now has a task-specific advisory layer.

A task can specify:

- maximum preferred latency;
- minimum quality score;
- maximum paid cost per request;
- local preference; and
- whether paid inference is allowed.

The advisor ranks eligible providers using:

- hard Private/Zero-Cost filters from the existing router;
- provider availability;
- circuit-breaker state;
- recent average latency;
- Model Arena quality evidence;
- estimated request cost; and
- explicit task objectives.

This remains advisory on top of the hard owner/routing constraints: a higher score cannot bypass Private mode, Zero-Cost mode, a circuit breaker or budget policy.

Core endpoints:

- `PUT /api/orchestrator/models/adaptive/tasks/{taskId}/objective`
- `GET /api/orchestrator/models/adaptive/tasks/{taskId}/objective`
- `GET /api/orchestrator/models/adaptive/tasks/{taskId}/rank`

## 6. Rate-limit and quota-reset awareness

Providers can now have a durable temporary backpressure state containing:

- blocked-until time;
- remaining units when known;
- quota-reset time when known; and
- reason.

Runtime failures that look like `429`, `rate limit`, or `too many requests` are treated as provider backpressure rather than ordinary model-quality failure.

A rate-limited provider is skipped until its window clears. This does not permit routing to an otherwise disallowed paid or remote provider.

Core endpoints:

- `GET /api/orchestrator/models/adaptive/rate-limits`
- `POST /api/orchestrator/models/adaptive/rate-limits/{providerId}`
- `DELETE /api/orchestrator/models/adaptive/rate-limits/{providerId}`

## 7. Automatic Model Arena runtime scoring

Stage 6 stored runtime Model Arena measurements. Stage 7 adds an initial deterministic automatic quality heuristic so every successful runtime result can produce quality evidence without pretending a separate judge model ran.

Evidence can now include:

- success/failure;
- latency;
- model/provider;
- scenario;
- deterministic response-quality heuristic; and
- explicit externally supplied quality scores from benchmark/evaluation workflows.

Task routing can consult the recent average quality evidence for a provider.

## 8. OS credential-vault production boundary

Stage 7 adds `OperatingSystemCredentialVault`, the production-facing contract for a future OS-backed secret store.

`WindowsDpapiVaultPlan` explicitly records that Windows Credential Manager / DPAPI support is **planned but not implemented**.

The existing environment-backed vault remains the portable development/CI backend. Stage 7 does not call DPAPI or claim hardware-backed storage exists before a real Windows workstation is available for validation.

## 9. Replay-protected host-daemon protocol

The simulated host command path now records durable command receipts.

For a simulated command:

- the Stage 6 signed/expiring envelope is verified;
- a receipt is stored by command id;
- the signature itself is not persisted, only a SHA-256 hash;
- an acknowledged/rejected status is recorded; and
- replaying an already-receipted command id is rejected.

This remains simulation-only. It is a protocol/replay-control layer, not a real Windows executor.

Core endpoints:

- `POST /api/orchestrator/hosts/protocol/simulate-once`
- `GET /api/orchestrator/hosts/protocol/receipts`

## 10. Syntra voice-session contracts

Stage 7 adds durable voice-session state for future streaming STT/TTS clients.

Voice sessions can be linked to a mission and/or task and support:

- transcript chunks;
- listening/speaking state;
- barge-in/interruption state; and
- deterministic priority commands.

Priority commands bypass model inference:

- `STOP`
- `PAUSE`
- `RESUME`
- `TAKE_CONTROL`

For task-bound sessions these invoke the existing deterministic task-control layer. A global `STOP` can engage the local emergency stop.

Core endpoints:

- `POST /api/orchestrator/voice/sessions`
- `GET /api/orchestrator/voice/sessions`
- `POST /api/orchestrator/voice/sessions/{id}/transcript`
- `POST /api/orchestrator/voice/sessions/{id}/barge-in`
- `POST /api/orchestrator/voice/sessions/{id}/priority/{command}`

Stage 7 defines control/session contracts; it does not pretend microphone capture, streaming speech recognition or TTS hardware integration is already complete.

## 11. Career and recruiter intelligence

Stage 7 adds a recruiter-readiness evidence audit covering:

- GitHub profile completeness;
- profile README;
- portfolio linkage;
- LinkedIn completeness;
- featured projects;
- project documentation;
- automated tests/CI evidence;
- screenshots/demos; and
- skills evidence.

It produces a 0–100 readiness score plus concrete improvement recommendations. It is analysis only and does not silently edit public profiles.

Core endpoints:

- `POST /api/orchestrator/career/audit`
- `GET /api/orchestrator/career/audits`

## 12. Governed trading intelligence

Stage 7 introduces trading **analysis**, not live execution.

The initial deterministic signal builder takes supplied market-analysis inputs and produces:

- symbol;
- `LONG`, `SHORT` or `NO_TRADE`;
- entry price;
- stop loss;
- TP1 / TP2 / TP3;
- leverage recommendation within the configured hard cap;
- risk per trade;
- reward:risk ratio;
- confidence;
- setup score;
- recommended notional estimate;
- `quickProfitCandidate` classification; and
- rationale.

Risk policy contains:

- risk per trade;
- max daily loss policy value;
- max leverage; and
- minimum reward:risk.

A tradable signal begins as `PENDING_ACCEPTANCE`. High-score signals can create an owner notification, including short-horizon/quick-profit candidates, but they do not execute automatically.

Owner decisions:

- `Accept analysis` -> `ACCEPTED_ANALYSIS`, execution state remains `NOT_EXECUTED`;
- `Reject` -> `REJECTED`.

There is **no Stage 7 broker/exchange order adapter**. Accepting a signal does not place an order. The subsystem does not guarantee profit.

Core endpoints:

- `GET /api/orchestrator/trading/risk-policy`
- `PUT /api/orchestrator/trading/risk-policy`
- `POST /api/orchestrator/trading/analyze`
- `GET /api/orchestrator/trading/signals`
- `POST /api/orchestrator/trading/signals/{id}/accept`
- `POST /api/orchestrator/trading/signals/{id}/reject`

## 13. Stage 7 Mission Control

The owner dashboard now surfaces:

- task pause/resume/take-control/cancel;
- scheduler workers and priority queue;
- proactive notifications;
- local knowledge/memory search;
- model health, budgets and rate-limit state;
- voice-session state;
- trading signals with entry/TP/SL/leverage/risk/R:R/confidence/setup score;
- `Accept analysis` / `Reject` controls only;
- recruiter-readiness audits;
- mission sessions;
- trusted Windows hosts;
- GitHub proposal approval/publish flow;
- MCP server/grant trust state;
- pending approvals; and
- invocation audit.

A visible banner states both hardware-safe and trading-analysis-only boundaries.

## Verification

GitHub Actions run 262 caught a missing `java.util.Locale` import in `KnowledgeEdgeEntity`. The defect was fixed rather than bypassed.

GitHub Actions run 263 then passed the dashboard, gateway, user, identity, audit and expanded orchestrator tests including the new Stage 7 integration suite.

GitHub Actions run 264 passed the complete Stage 7 code plus the Stage 7 Mission Control dashboard.

The Stage 7 integration suite verifies:

- scheduler priority and concurrency limits;
- mission dependency blocking/release;
- protected-memory filtering;
- rate-limit backpressure windows;
- proactive failure notification;
- deterministic voice PAUSE without model inference;
- career-readiness scoring;
- trading signal TP/SL/leverage output and owner-acceptance-only behavior;
- `NOT_EXECUTED` after trading analysis acceptance;
- RSA host pairing + replay rejection; and
- the explicit unimplemented Windows DPAPI boundary.

## Safety boundaries retained

- `main` remains untouched while this work stays in the draft feature PR.
- Real Windows OS actions remain simulation-only until a target PC is available and production host execution is separately implemented/tested.
- Private/protected-data model routing cannot silently fall back off-device.
- Zero-Cost routing cannot silently fall back to paid inference.
- Provider rate-limit fallback cannot bypass locality/cost rules.
- Credential values are not persisted in model/task/audit entities.
- MCP remains exact-grant/schema governed.
- GitHub publishing remains proposal-specific and approval-gated.
- Emergency stop and priority voice controls remain deterministic.
- Offensive-security automation remains limited to owner-controlled/explicitly authorized systems or isolated labs.
- Trading analysis does not guarantee profit and does not place live orders in Stage 7.

## Next — Stage 8

Stage 8 should deepen intelligence and evaluation without crossing the hardware/live-money boundaries prematurely:

1. local embedding adapter + vector memory index with deterministic lexical fallback;
2. knowledge ingestion pipelines for repositories, project docs and owner-selected files;
3. durable autonomous dispatcher loop with database-safe claims, fairness and multi-worker coordination;
4. mission planner proposal mode using LLM planning followed by deterministic graph/risk validation before task creation;
5. evaluation harness for agent task success, correction count, latency, cost, hallucination/evidence quality and regression benchmarks;
6. notification delivery adapters for desktop/mobile/email where connected and owner-approved;
7. streaming STT/TTS adapter interfaces, VAD/turn-state events and barge-in timing metrics;
8. read-only career connectors and recruiter simulation using verified public/project evidence;
9. trading backtesting engine, walk-forward/out-of-sample evaluation, paper portfolio ledger and journal;
10. market-data adapter interfaces with freshness/source metadata and fail-closed stale-data handling;
11. deterministic portfolio/risk officer with daily loss, correlated exposure and leverage gates for paper trading;
12. optional paper-trading exchange adapter only after tests; live-money execution remains a later separately opted-in stage;
13. Windows host-daemon implementation planning for app launch/telemetry, still gated on the actual workstation;
14. desktop/mobile remote companion protocol over an authenticated private channel;
15. production policy/evaluation report showing exactly which capabilities are safe to enable when the target PC arrives.
