# Stage 9 — Adaptive Intelligence & Paper Portfolio

Stage 9 extends Syntra + Aetheris from the Stage 8 intelligence/evaluation lab into a more adaptive, incrementally synchronized and evidence-driven personal-AI runtime while preserving every Stage 8 safety boundary.

Stage 9 deliberately does **not** enable live-money trading, withdrawals, unrestricted Windows execution, a raw public remote-agent API, or unverified speech-hardware integration.

## 1. Stage 8 compatibility is preserved

Stage 8's generic vector-memory contract remains unchanged:

- `EmbeddingAdapter` still resolves to `local-hash-v1` by default;
- the Stage 8 vector index therefore retains its original deterministic behavior;
- Stage 9 adds a **separate** adaptive index using adapter id `adaptive-local-v2`;
- Stage 9 tombstones are also filtered from Stage 8 lexical/vector retrieval so superseded knowledge does not leak back into results.

This separation was introduced after CI correctly detected that making the Stage 9 adapter primary would silently alter the Stage 8 contract.

## 2. Adaptive local embeddings with deterministic fallback

`AdaptiveLocalEmbeddingAdapter` is a replaceable local-only embedding adapter.

By default neural embeddings are disabled. When explicitly enabled it can call a local Ollama-compatible `/api/embed` endpoint and records:

- backend;
- model;
- whether neural inference was attempted;
- whether neural inference succeeded;
- whether the deterministic hash fallback was used;
- output dimensions;
- latency;
- approximate JVM heap delta; and
- a timestamped runtime detail.

If the local neural endpoint is disabled, unavailable, malformed or returns an error, Stage 9 falls back to `local-hash-v1` rather than silently routing embeddings to a cloud service.

The neural vector is deterministically projected into the existing 96-dimension memory width so the new Stage 9 index remains bounded and compatible with local persistence.

Core endpoints:

- `GET /api/orchestrator/stage9/embedding/runtime`
- `POST /api/orchestrator/stage9/embedding/rebuild`
- `GET /api/orchestrator/stage9/memory/search?q=...`

## 3. Incremental knowledge synchronization

Stage 9 adds durable ingestion-source state keyed by:

- source kind;
- source id; and
- namespace.

Each source records:

- SHA-256 content hash;
- revision;
- active knowledge-node ids;
- chunk count;
- scope;
- tombstone state; and
- update time.

Behavior:

1. owner-selected source content is hashed;
2. unchanged content is skipped rather than re-ingested;
3. changed content tombstones superseded chunks;
4. new chunks are written through the existing Stage 8 knowledge pipeline;
5. active chunks are indexed into the separate Stage 9 adaptive vector index; and
6. deletion/tombstone hides superseded content from lexical and vector retrieval.

There is no silent full-disk or repository crawl. Sources remain explicit and owner/project scoped.

Core endpoints:

- `POST /api/orchestrator/stage9/ingestion/sync`
- `POST /api/orchestrator/stage9/ingestion/tombstone`
- `GET /api/orchestrator/stage9/ingestion/sources`

## 4. PostgreSQL-safe capability-aware dispatch

Stage 9 strengthens autonomous multi-worker dispatch without replacing the Stage 7/8 durable scheduler.

It adds:

- worker capability profiles;
- allowed execution lanes;
- per-work-item capability requirements;
- pessimistic database locking of queued scheduler tickets;
- online heartbeat checks;
- existing worker concurrency limits;
- existing durable work-item readiness and leases;
- capability matching; and
- deterministic fairness ordering.

Fairness selects, in order:

1. the lowest worker utilization ratio;
2. the worker least recently assigned Stage 9 work; and
3. stable worker id ordering.

A Stage 9 dispatcher claim still uses the existing `DurableWorkQueueService.claim(...)`, so retry, lease, emergency-stop and backpressure semantics remain authoritative.

Core endpoints:

- `PUT /api/orchestrator/stage9/workers/{workerId}/capabilities`
- `PUT /api/orchestrator/stage9/work/{workItemId}/requirements`
- `GET /api/orchestrator/stage9/workers/capabilities`
- `POST /api/orchestrator/stage9/dispatch`

## 5. Mission dry-run evaluator

Before a proposed mission is materialized, Stage 9 can run a deterministic dry-run that predicts:

- step count;
- dependency edges;
- approximate root parallelism;
- risk level;
- whether approval is expected;
- advisory maximum paid inference cost;
- evidence requirements; and
- warnings.

The dry-run validates:

- required step fields;
- known agents;
- dependency existence;
- no self-dependencies;
- no dependency cycles;
- maximum 24 steps; and
- explicit unsafe-command blocks.

Unsafe dry-run commands include attempts to withdraw or transfer funds, place live trades, disable safety, steal credentials, format disks, run `rm -rf /`, or bypass approvals.

`ZERO_COST`, `PRIVATE`, or `allowPaid=false` yields a paid-cost estimate of zero.

The dry-run does not create tasks, execute tools, send external actions or execute financial operations.

Core endpoint:

- `POST /api/orchestrator/stage9/missions/dry-run`

## 6. Benchmark packs and regression evidence

Stage 9 adds domain benchmark packs on top of the existing durable evaluation service.

Initial packs:

- `coding-core` — implementation, debugging, tests and security review;
- `research-grounding` — source synthesis, contradiction checks and uncertainty;
- `university-tutor` — explanation, worked examples and exam practice;
- `pc-operations` — diagnosis, safe remediation and rollback planning; and
- `multi-agent` — planning, delegation, cross-review and failure recovery.

A benchmark run records the existing evaluation dimensions:

- success;
- correction count;
- latency;
- cost;
- evidence score;
- hallucination penalty; and
- calculated overall score.

Benchmark verdicts are:

- `PASS_STRONG` at 80+;
- `PASS` at 65+;
- `NEEDS_REVIEW` at 50+; and
- `FAIL` below 50.

Core endpoints:

- `GET /api/orchestrator/stage9/benchmarks`
- `POST /api/orchestrator/stage9/benchmarks/run`

## 7. Read-only career evidence

Stage 9 can inspect an allowlisted GitHub repository file through the existing governed GitHub adapter and score explicit career evidence such as:

- setup / installation documentation;
- tests or CI;
- architecture explanation;
- screenshots, demos or previews;
- security thinking;
- deployment/operations documentation; and
- API documentation.

The connector is read-only for this workflow. Public-profile writes remain separately approval-gated, and Stage 9 does not claim an automated LinkedIn write integration.

Core endpoint:

- `POST /api/orchestrator/stage9/boundaries/career/github-evidence`

## 8. Redundant market-data consensus

Stage 9 adds a stricter market-data path for new paper operations.

A consensus requires at least two named sources and validates:

- source identity;
- fresh observation timestamps;
- maximum future clock skew;
- maximum cross-source timestamp skew;
- minimum number of fresh sources; and
- maximum latest-price divergence.

If these conditions fail, the consensus fails closed.

When sources agree, Stage 9 creates median OHLCV bars across aligned observations and persists consensus evidence containing:

- symbol;
- contributing sources;
- median reference price;
- maximum price divergence; and
- clock skew.

This is still a read-only market-data abstraction. Stage 9 does not claim a particular live exchange data adapter is connected.

Core endpoints:

- `POST /api/orchestrator/stage9/trading/market-data`
- `GET /api/orchestrator/stage9/trading/market-consensus/{symbol}`
- `GET /api/orchestrator/stage9/trading/market-consensus/evidence`

## 9. Cost-aware walk-forward backtesting

Stage 9's advanced deterministic moving-average baseline adds:

- fees;
- spread;
- slippage;
- per-held-bar funding;
- multiple walk-forward windows;
- net return;
- win rate;
- maximum drawdown;
- accumulated simulated costs; and
- simple `TREND_UP`, `TREND_DOWN`, `RANGE` regime counts.

Backtest results are persisted for later comparison and regression analysis.

Backtests remain simplified simulations and are not evidence of future profit.

Core endpoints:

- `POST /api/orchestrator/stage9/trading/backtests`
- `GET /api/orchestrator/stage9/trading/backtests`

## 10. Mark-to-market paper portfolio

Stage 9 extends the durable Stage 8 paper account with mark-to-market evidence.

For each open paper position it uses fresh Stage 9 market consensus to calculate:

- current reference price;
- unrealized P&L;
- total paper equity;
- cash balance;
- gross exposure; and
- number of open positions.

Equity snapshots are persisted.

Stage 9 can also calculate pairwise Pearson return correlation estimates for currently open paper symbols when enough consensus history exists.

Core endpoints:

- `POST /api/orchestrator/stage9/trading/paper/mark`
- `GET /api/orchestrator/stage9/trading/paper/equity`
- `GET /api/orchestrator/stage9/trading/paper/correlations`

## 11. Consensus-gated paper execution

Stage 9 does not replace the deterministic Stage 8 Risk Officer.

The Stage 9 paper-open path is:

`accepted analysis -> fresh multi-source consensus -> Stage 8 market snapshot -> deterministic Paper Risk Officer -> simulated PAPER_ONLY fill`

Therefore:

- a signal that has not been explicitly accepted is rejected;
- stale or disagreeing market sources fail closed;
- Stage 8 stop-loss, leverage, reward:risk, daily-loss, open-position and exposure gates remain authoritative; and
- a successful fill is explicitly labeled `PAPER_ONLY`.

Core endpoint:

- `POST /api/orchestrator/stage9/trading/paper/open/{signalId}`

## 12. Exchange testnet boundary

Stage 9 records a testnet plan but does **not** connect an exchange adapter.

Current state:

- adapter: not connected;
- live money: disabled;
- withdrawals: disabled;
- transfers: no path;
- credential policy: `NEW_OWNER_CREDENTIALS_VIA_VAULT_ONLY`.

Credentials previously pasted into chats must never be reused. A future testnet connection requires newly supplied owner credentials stored through the credential vault.

Core endpoint:

- `GET /api/orchestrator/stage9/trading/testnet-plan`

## 13. Hardware and remote boundaries

Stage 9 makes future production boundaries explicit instead of pretending they are already deployed.

### Local speech

State: `HARDWARE_GATED`

Existing streaming STT/TTS interfaces and deterministic voice priority controls remain available, but real microphone capture, VAD, local STT and local TTS require validation on the actual workstation.

### Windows host daemon

State: `HARDWARE_GATED`

The signed/replay-protected host protocol exists, but real Windows execution remains disabled. Planned least-privilege handlers are:

- app launch;
- process status;
- system telemetry; and
- workspace file open.

Admin access is not the default and UAC/security bypass is not permitted.

### Remote companion transport

State: `CONTRACT_ONLY`

A production remote channel must require:

- encrypted transport;
- mutual device authentication;
- private-network preference;
- expiring/revocable capability-scoped pairing; and
- no raw public agent API.

Core endpoints:

- `GET /api/orchestrator/stage9/boundaries/speech`
- `GET /api/orchestrator/stage9/boundaries/windows-host`
- `GET /api/orchestrator/stage9/boundaries/remote-transport`
- `GET /api/orchestrator/stage9/boundaries/public-profiles`

## 14. Stage 9 console

The authenticated owner console is available at:

- `/stage9.html`

It surfaces real persisted/runtime evidence for:

- adaptive embedding backend and fallback;
- knowledge source revisions/tombstones;
- benchmark packs;
- worker capability profiles;
- market consensus evidence;
- advanced backtests;
- paper equity snapshots;
- owner policy; and
- hardware/remote capability boundaries.

The UI escapes API-derived strings before HTML rendering and keeps these banners visible:

- `LIVE MONEY · DISABLED`
- `WINDOWS + SPEECH · HARDWARE-GATED`
- `STAGE 8 COMPATIBILITY · PRESERVED`

## 15. Verification history

Stage 9 CI caught real integration defects and they were repaired rather than bypassed:

- run 277: Stage 8 expected `local-hash-v1`; Stage 9 had temporarily become the primary embedding adapter. Architecture was corrected to preserve the Stage 8 index and create a separate Stage 9 adaptive index.
- run 278: Java rejected a mutable loop-variable lambda capture in the consensus bar merger. The implementation was corrected with a per-iteration final index.
- run 281: the new test referenced `executionState()` while `PaperExecutionResult` correctly exposes `mode()`. The test was corrected to assert `mode() == PAPER_ONLY`.
- run 284: the synthetic benchmark evaluation scored 68.2 while the test incorrectly assumed 80+. The test was aligned with the benchmark service's actual `PASS >= 65` contract; the production evaluator was not inflated.
- run 285: the complete Stage 9 implementation and dedicated integration suite passed dashboard, gateway, user, identity, audit and orchestrator checks.

The Stage 9 integration suite explicitly verifies:

- Stage 8 embedding identity remains `local-hash-v1`;
- Stage 9 adaptive fallback is measured;
- unchanged ingestion is skipped;
- tombstoned knowledge disappears from Stage 9 retrieval;
- unsafe mission dry-runs fail;
- capability-aware work reaches the compatible worker;
- benchmark evaluation is persisted;
- market-source disagreement fails closed;
- advanced backtests include costs and walk-forward evidence;
- unaccepted signals fail the paper Risk Officer;
- accepted signals can produce only `PAPER_ONLY` simulated fills; and
- live-money/testnet withdrawals remain disabled.

## Safety boundaries retained

- `main` remains untouched while Stage 9 stays in draft PR #7.
- Private/protected-data routing cannot silently fall back off-device.
- Zero-Cost mode cannot silently route to paid inference.
- Adaptive embeddings never silently fall back to cloud inference.
- Incremental ingestion does not silently crawl owner files.
- Model-generated/dry-run mission plans cannot bypass deterministic graph/safety checks.
- Worker capability matching cannot bypass durable queue leases/concurrency/backpressure.
- External public-profile writes remain approval-gated.
- Remote operation requires a future authenticated encrypted private transport.
- Real Windows execution and local speech hardware remain hardware-gated.
- Offensive-security work remains limited to owner-controlled, explicitly authorized targets or isolated labs.
- Market data must pass freshness, skew and disagreement checks for the Stage 9 consensus paper path.
- AI/model outputs cannot bypass the deterministic Paper Risk Officer.
- Stage 9 trading remains paper-only.
- Live orders, withdrawals and transfers remain disabled.

## Next — Stage 10

Stage 10 should move from hardware-gated contracts toward validated workstation deployment without relaxing the current boundaries:

1. target-PC bootstrap/agent service with explicit installation and rollback;
2. least-privilege Windows host handlers for app launch, process status, telemetry and approved workspace-file operations;
3. Windows Credential Manager / DPAPI-backed vault validated on the real PC;
4. local STT/TTS/VAD adapters benchmarked on the actual RTX-class workstation, including barge-in targets;
5. authenticated encrypted private remote transport with device pairing, revocation and recovery;
6. incremental repository/file watchers limited to owner-configured roots;
7. automatic benchmark regression gates before agent/model changes are promoted;
8. production notification adapters only where the owner connects a provider and approves the action class;
9. real redundant read-only market-data adapters with source-health telemetry;
10. richer paper-execution simulation including partial fills, order types, fees, slippage and rejected-order scenarios;
11. optional exchange testnet adapter only after newly supplied credentials are stored in the vault;
12. live-money execution remains disabled and requires a later separately designed explicit opt-in stage;
13. desktop shell performance profiling, installer packaging and recovery testing;
14. production observability/SLOs for local services, agent latency, queue health, memory, speech and remote connectivity; and
15. owner-facing backup, recovery and rollback runbooks for the complete Syntra + Aetheris workstation.
