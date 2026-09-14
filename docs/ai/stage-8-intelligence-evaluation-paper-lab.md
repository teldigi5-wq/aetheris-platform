# Stage 8 — Intelligence, Evaluation & Paper Lab

Stage 8 moves Syntra + Aetheris from the Stage 7 Personal Intelligence OS toward a measurable autonomous operating environment. It adds local vector retrieval, ingestion, an autonomous dispatcher coordinator, model-proposed mission plans that remain deterministically validated, evaluation evidence, voice-streaming contracts, recruiter simulation, capability-scoped remote pairing, and a paper-only trading laboratory.

The two hard boundaries remain unchanged:

- real Windows execution is still hardware-gated and not implemented as a production executor;
- financial execution is paper-only in Stage 8. There is no broker/exchange live-order adapter.

## 1. Local vector memory with deterministic fallback

Stage 8 adds an `EmbeddingAdapter` boundary and a zero-cost local `LocalHashEmbeddingAdapter`.

The initial adapter intentionally avoids an external dependency. It creates deterministic normalized 96-dimensional feature vectors from local text. This is not presented as a frontier semantic-embedding model; the adapter exists so a stronger local embedding model can replace it later without changing memory contracts.

`VectorMemoryIndexService`:

- indexes existing knowledge nodes;
- persists vectors durably;
- searches by cosine similarity;
- preserves memory scope/namespace filters;
- never returns protected nodes unless explicitly requested; and
- falls back to the Stage 7 lexical search path if vector retrieval cannot produce useful candidates.

Endpoints:

- `POST /api/orchestrator/stage8/memory/rebuild`
- `GET /api/orchestrator/stage8/memory/search`

## 2. Knowledge ingestion

`KnowledgeIngestionService` accepts owner-selected content from four declared source classes:

- `REPOSITORY`
- `DOCUMENT`
- `OWNER_FILE`
- `TEXT`

Ingestion:

1. validates the source class and source identifier;
2. chunks content into bounded units;
3. writes the chunks through the existing Stage 7 knowledge graph;
4. preserves protected-data classification;
5. tags the source type and ingestion state; and
6. indexes each created node through the local embedding adapter.

Endpoint:

- `POST /api/orchestrator/stage8/memory/ingest`

The current endpoint receives already-selected text. It does not silently crawl the workstation or upload arbitrary owner files.

## 3. Autonomous dispatcher coordinator

`AutonomousDispatcherService` adds a scheduled coordination loop on top of the existing Stage 7 scheduler.

It does not bypass queue governance. Each tick:

1. asks the existing scheduler to recover stale workers;
2. reads registered worker heartbeats;
3. orders workers by utilization;
4. computes available concurrency slots;
5. calls the existing scheduler dispatch path; and
6. therefore retains leases, priorities, concurrency limits and queue backpressure.

The dispatcher can also be ticked manually for inspection.

Endpoints:

- `GET /api/orchestrator/stage8/dispatcher/last`
- `POST /api/orchestrator/stage8/dispatcher/tick`

## 4. Mission-plan proposal + deterministic validation

Stage 8 can ask the existing model execution fabric to propose a mission DAG, but the model cannot create tasks directly.

The proposal must use a strict line protocol:

`STEP|key|title|agentId|priority|commaSeparatedDependencies|command`

Before optional materialization, deterministic code validates:

- known specialist agent ids;
- unique step keys;
- dependency existence;
- no self-dependency;
- no graph cycles;
- a maximum of 24 steps; and
- explicit dangerous-command deny patterns including live trading, withdrawals, credential theft, safety bypasses and destructive host commands.

A raw-plan validation endpoint is also available so the validation layer can be tested without inference.

Endpoints:

- `POST /api/orchestrator/stage8/missions/{missionId}/propose`
- `POST /api/orchestrator/stage8/missions/{missionId}/validate`

## 5. Evaluation harness

Stage 8 adds durable evaluation evidence for agents/models/workflows.

An evaluation records:

- subject type and id;
- scenario;
- success/failure;
- correction count;
- latency;
- estimated cost;
- evidence score;
- hallucination penalty; and
- a deterministic overall score.

This creates a regression/evidence layer without letting a model grade itself as the sole authority.

Endpoints:

- `POST /api/orchestrator/stage8/evaluations`
- `GET /api/orchestrator/stage8/evaluations`
- `GET /api/orchestrator/stage8/evaluations/average`

## 6. Voice streaming contracts and latency evidence

Stage 8 introduces replaceable `SpeechToTextAdapter` and `TextToSpeechAdapter` contracts plus turn metrics for:

- VAD/speech timing;
- first partial transcript latency;
- final transcript latency;
- TTS start; and
- barge-in-to-TTS-stop latency.

No microphone, speaker, Whisper, Windows audio or production TTS adapter is claimed to be connected yet. The adapter-status response explicitly reports that hardware integration is false.

Endpoints:

- `GET /api/orchestrator/stage8/voice/adapters`
- `POST /api/orchestrator/stage8/voice/metrics`
- `GET /api/orchestrator/stage8/voice/metrics`

The Stage 7 deterministic STOP/PAUSE/RESUME/TAKE_CONTROL path remains authoritative.

## 7. Career recruiter simulation

Stage 8 adds a recruiter simulation layer over durable Stage 7 career-readiness audits.

It combines:

- verified readiness evidence;
- an explicit interview-preparation score; and
- available read-only career evidence connectors.

The resulting screening verdict is one of:

- `STRONG_SHORTLIST`
- `COMPETITIVE`
- `BORDERLINE`
- `NOT_READY`

The connector interface is explicitly read-only. Stage 8 does not claim automatic LinkedIn editing or posting.

Endpoints:

- `POST /api/orchestrator/stage8/career/simulate`
- `GET /api/orchestrator/stage8/career/connectors`

## 8. External notification delivery boundary

Stage 8 defines delivery channels for in-app, desktop, mobile and email notifications.

Rules:

- in-app notification is available through the existing notification center;
- an external channel requires exact owner approval for the task/channel;
- even after approval, Stage 8 returns `ADAPTER_NOT_CONNECTED` until a real provider is configured.

This avoids pretending that a notification was sent.

Endpoint:

- `POST /api/orchestrator/stage8/notifications/deliver`

## 9. Authenticated remote companion contract

Stage 8 adds a private remote-companion pairing/session contract.

A session:

- receives a random pairing token once;
- stores only the SHA-256 token hash;
- has a bounded expiry;
- is revocable; and
- contains an explicit capability set.

Allowed Stage 8 remote capabilities are intentionally narrow:

- `READ_STATUS`
- `VIEW_NOTIFICATIONS`
- `PAUSE_TASK`
- `STOP_TASK`
- `TAKE_CONTROL`

The current transport string is a contract marker, not a claim that a production VPN/WebSocket/mTLS tunnel has already been deployed.

Endpoints:

- `POST /api/orchestrator/stage8/remote/pair`
- `POST /api/orchestrator/stage8/remote/{id}/authenticate`
- `POST /api/orchestrator/stage8/remote/{id}/revoke`

## 10. Market-data freshness boundary

`MarketDataAdapter` creates a replaceable read-only market-data interface.

The first Stage 8 implementation is a manual/synthetic adapter used for integration testing and future connector development. Every snapshot carries:

- symbol;
- source;
- observation timestamp; and
- OHLCV bars.

Consumers pass a maximum age. Stale data throws and fails closed rather than silently producing a paper fill from old prices.

## 11. Backtesting / out-of-sample evidence

The initial `BacktestService` implements a deterministic moving-average strategy laboratory.

It requires at least 30 bars and separates data into a training segment and a later test segment. Results persist:

- train return;
- test return;
- trade count;
- win rate;
- max drawdown; and
- final balance.

This is a baseline engineering/evaluation harness, not a profit prediction or guarantee. Future stages can add fees, slippage, funding, richer walk-forward windows and regime analysis.

Endpoints:

- `POST /api/orchestrator/stage8/trading/backtests`
- `GET /api/orchestrator/stage8/trading/backtests`

## 12. Deterministic paper Risk Officer

The paper-risk layer sits between accepted Stage 7 analysis and any simulated position.

It enforces:

- explicit `ACCEPTED_ANALYSIS` status;
- no execution of `NO_TRADE` signals;
- stop-loss requirement;
- maximum leverage from the Stage 7 trading-risk policy;
- minimum reward:risk;
- maximum open positions;
- maximum daily realized loss;
- maximum gross exposure;
- maximum per-symbol exposure; and
- maximum correlated exposure.

The LLM/model cannot override these checks.

Paper-risk policy endpoints:

- `GET /api/orchestrator/stage8/trading/paper/risk-policy`
- `PUT /api/orchestrator/stage8/trading/paper/risk-policy`

## 13. Paper portfolio and journal

The Stage 8 paper account starts with a configurable-in-code synthetic balance of 10,000 in the initial implementation.

A paper open:

1. requires a Stage 7 accepted analysis signal;
2. requires fresh market data;
3. runs the deterministic Risk Officer;
4. calculates a simulated fill/quantity;
5. persists a paper position; and
6. journals that no exchange order was sent.

A paper close calculates simulated realized P&L and journals the close.

Endpoints:

- `GET /api/orchestrator/stage8/trading/paper/account`
- `POST /api/orchestrator/stage8/trading/paper/open/{signalId}`
- `POST /api/orchestrator/stage8/trading/paper/positions/{positionId}/close`
- `GET /api/orchestrator/stage8/trading/paper/positions`
- `GET /api/orchestrator/stage8/trading/paper/journal`

There is no live broker/exchange order endpoint in Stage 8.

## 14. Stage 8 Lab Console

The dashboard now ships `/stage8.html`, linked from the main dashboard shell.

It reuses the authenticated Aetheris session and displays:

- dispatcher status;
- evaluation evidence;
- local vector-memory search;
- voice latency measurements;
- backtest evidence;
- paper account balance;
- paper positions;
- paper trade journal; and
- explicit remote/notification/hardware capability boundaries.

A visible banner states `PAPER ONLY · NO LIVE ORDERS`.

API-derived strings are escaped before HTML rendering.

## 15. Verification

Stage 8 validation is fail-forward rather than bypass-oriented:

- run 268 exposed two compile-time accessor mismatches against existing Stage 7 record contracts;
- the dispatcher was corrected to use the scheduler `reason()` accessor;
- the mission proposal path was corrected to use the model response `text()` accessor;
- run 270 passed the corrected Stage 8 core across dashboard + all backend services;
- run 271 passed the dedicated Stage 8 integration suite.

The Stage 8 integration suite verifies:

- vector ingestion + protected-memory filtering;
- durable evaluation evidence;
- valid DAG parsing and dangerous-plan rejection;
- voice latency metrics without claiming hardware integration;
- remote token/capability enforcement and revocation;
- stale market-data rejection;
- train/test backtest persistence;
- accepted analysis opening only a paper position; and
- unaccepted signals being rejected by the deterministic Risk Officer.

The final dashboard/documentation head must also pass CI before Stage 8 is marked complete.

## Safety boundaries retained

- Private/protected data remains local unless explicitly allowed by higher-level policy.
- Zero-Cost mode cannot silently use a paid model.
- Model-generated plans cannot directly create arbitrary actions without deterministic parsing/validation.
- Emergency/priority owner control remains deterministic.
- External notifications cannot pretend delivery.
- Remote companion capabilities are explicit, expiring and revocable.
- Market data must be fresh enough for paper execution.
- Trading models/signals cannot bypass the Risk Officer.
- Paper fills are simulations and never call an exchange.
- No withdrawal/transfer path exists.
- Real Windows execution remains hardware-gated.

## Stage 9 candidates

1. replaceable true local embedding-model adapter with measured CPU/GPU footprint and lexical fallback;
2. incremental repository/document ingestion with source hashes, change detection and deletion/tombstone handling;
3. PostgreSQL-safe multi-dispatcher locking/fairness and worker capability matching;
4. mission-plan evaluator with simulation/dry-run cost/risk estimates before materialization;
5. benchmark packs for coding, research, university tutoring, PC operations and agent coordination;
6. local STT/TTS adapters on the actual target workstation with VAD and barge-in latency targets;
7. production private remote transport design (mTLS/private tunnel/WebSocket) with device revocation;
8. real read-only public-profile/GitHub evidence connectors where supported;
9. read-only market-data connector with source redundancy and freshness/clock-skew checks;
10. backtesting with fees, spread, slippage, funding, multi-window walk-forward and regime breakdowns;
11. persistent paper equity curve, mark-to-market unrealized P&L and portfolio correlation estimates;
12. optional exchange testnet/paper adapter using new owner-provided credentials stored only in the vault — never reuse credentials from chat history;
13. no live-money execution until a later, separately designed and explicitly opted-in stage;
14. Windows host-daemon implementation on the real target PC with least-privilege capability handlers;
15. Stage 9 owner policy report mapping every capability to Observe / Auto-safe / Approval / Disabled states.
