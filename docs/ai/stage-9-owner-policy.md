# Stage 9 — Owner Capability Policy

This document is the Stage 9 capability truth table for Syntra + Aetheris. It separates what the runtime can safely automate now from what remains observe-only, approval-gated, paper-only, hardware-gated or disabled.

The policy is intentionally conservative: model confidence, agent voting, convenience or user-interface state cannot silently promote a capability into a more permissive class.

## Policy classes

### `AUTO_SAFE`

The runtime may perform the action automatically when its existing deterministic constraints are satisfied. The action must be local/reversible or otherwise explicitly classified as low-risk.

### `OBSERVE`

The runtime may inspect, analyze, score or simulate, but may not perform the consequential external action.

### `APPROVAL`

The runtime may prepare an action, but an explicit owner approval and the appropriate connected adapter/capability are required before execution.

### `DISABLED`

The runtime must not execute the capability in Stage 9. Enabling it requires a later implementation stage and separate validation/opt-in.

## Stage 9 matrix

| Capability | Stage 9 mode | Hard boundary |
|---|---|---|
| Stage 8 local-hash memory | `AUTO_SAFE` | Local deterministic index; Stage 8 identity remains `local-hash-v1`. |
| Stage 9 adaptive neural embeddings | `AUTO_SAFE` | Local endpoint only; deterministic hash fallback; no cloud embedding fallback. |
| Owner-selected knowledge synchronization | `AUTO_SAFE` | Explicit sources only; source hashes/revisions/tombstones; no silent filesystem crawl. |
| Stage 9 adaptive index rebuild | `AUTO_SAFE` | Local knowledge only; protected-data filtering remains enforced. |
| Mission dry-run | `OBSERVE` | Validates/plans only; cannot create tasks, call tools or execute financial/external actions. |
| Mission materialization | Existing Stage 7/8 rules | Must pass deterministic DAG/risk validation and all existing approval rules. |
| Capability-aware worker dispatch | `AUTO_SAFE` | Existing durable queue, leases, concurrency, backpressure and emergency stop remain authoritative. |
| Benchmark/evaluation recording | `AUTO_SAFE` | Records evidence; cannot self-promote a model/agent past owner rules. |
| GitHub career evidence read | `OBSERVE` | Existing repository allowlist/read adapter; no public mutation. |
| LinkedIn/public profile analysis | `OBSERVE` | Analysis only when evidence is available. |
| Public profile writes | `APPROVAL` | Explicit owner approval plus a legitimate connected action path. |
| External desktop/mobile/email notification | `APPROVAL` | Real connected adapter required; never claim delivery when absent. |
| Remote companion operation | `APPROVAL` | Expiring/revocable capability-scoped pairing plus authenticated encrypted private transport. |
| Raw public agent API | `DISABLED` | Do not expose a general unauthenticated/public command surface. |
| Real Windows execution | `DISABLED` | Hardware-gated until least-privilege handlers are tested on the actual owner workstation. |
| Windows admin/UAC bypass | `DISABLED` | Never bypass OS security boundaries. |
| Real microphone/STT/TTS/VAD | `DISABLED` until hardware validation | Interfaces exist, but real local devices/engines are not claimed connected in Stage 9. |
| Priority voice `STOP/PAUSE/RESUME/TAKE_CONTROL` | Existing deterministic control | Must remain outside model inference and take priority over normal agent reasoning. |
| Market-data consensus | `OBSERVE` / paper-support | Requires multiple named fresh sources with clock-skew and divergence checks. |
| Backtesting | `OBSERVE` | Simulation/evaluation only; results are not profit guarantees. |
| Mark-to-market paper portfolio | `AUTO_SAFE` | Uses only simulated paper positions and freshness-checked consensus data. |
| Opening a paper position | `APPROVAL` | Signal analysis must already be explicitly accepted and the deterministic Paper Risk Officer must allow it. |
| Exchange testnet adapter | `DISABLED` | No adapter connected; requires newly supplied owner credentials stored in the vault. |
| Live-money order execution | `DISABLED` | No live order path exists. |
| Withdrawals / transfers | `DISABLED` | No withdrawal or transfer path exists. |

## Rule precedence

Stage 9 keeps the existing rule hierarchy:

1. non-bypassable platform/legal/provider/runtime constraints;
2. owner security rules;
3. owner cost/privacy/behavior rules;
4. project rules;
5. agent rules;
6. session rules; and
7. the current instruction.

A lower-priority layer cannot weaken a higher-priority rule.

Examples:

- a high-confidence model cannot bypass `PRIVATE` locality requirements;
- a consensus of several agents cannot enable a disabled live-money path;
- a paper-trading signal cannot bypass explicit analysis acceptance or the Risk Officer;
- an LLM-generated mission cannot bypass dry-run/graph/safety validation;
- a remote device cannot obtain capabilities that were not explicitly granted during pairing.

## Knowledge and privacy policy

### Adaptive embeddings

Adaptive neural embeddings are permitted only through a configured **local** embedding endpoint. If it is unavailable, Stage 9 falls back to the deterministic local-hash adapter. It must not silently send protected knowledge to a cloud embedding service.

### Incremental ingestion

The ingestion engine may process only explicitly supplied/selected source content. It does not have permission to recursively crawl arbitrary disks, home folders, browser profiles or credential locations simply because those paths exist.

A changed source supersedes old chunks through tombstones. A deleted source must remain excluded from active search.

### Protected data

Protected knowledge remains excluded from ordinary searches unless the caller explicitly requests protected results and the surrounding owner/routing policy permits access.

## Autonomous execution policy

Capability-aware dispatch is an optimization layer, not a new authority layer.

It may select a compatible worker automatically only when:

- the work item is already ready in the durable queue;
- the scheduler ticket remains queued;
- the worker heartbeat is current;
- the worker has free concurrency;
- required capabilities and lane match; and
- the existing queue allows a lease claim.

Pessimistic database locking protects queued-ticket selection from multi-dispatcher races. Existing retry, lease-expiry, backpressure, emergency-stop and task-state rules still control execution.

## Mission policy

A dry-run may predict risk, approvals, evidence and cost, but is strictly non-executing.

The following are rejected at the dry-run boundary rather than delegated to an LLM's judgment:

- live trades;
- withdrawals/transfers;
- credential theft;
- safety/approval bypass;
- disk formatting/destructive root deletion; and
- commands whose stated purpose is disabling safeguards.

Consequential but potentially legitimate operations such as deployments, public publishing, important deletion, drivers/registry/admin operations or financial actions must be surfaced as approval-requiring rather than silently executed.

## Career/public-profile policy

Stage 9 may use the existing governed GitHub adapter to inspect allowlisted repository files for recruiter evidence.

It may not silently:

- rewrite a public GitHub profile;
- push public changes merely because a recruiter score is low;
- edit LinkedIn;
- post messages/content; or
- alter the owner's public identity.

Those actions remain separate, proposal-specific and approval-gated.

## Remote companion policy

Stage 8 pairing already provides expiring/revocable capability scopes. Stage 9 defines the production transport requirement around that pairing:

- encrypted authenticated transport;
- mutual device identity;
- private-network/tunnel preference;
- narrow capability grants;
- explicit revocation; and
- no raw public general-purpose agent endpoint.

Until a real transport is implemented and validated, the production remote channel remains `CONTRACT_ONLY`.

## Windows policy

Real Windows host execution remains disabled in Stage 9.

Future least-privilege handlers are limited initially to:

- app launch;
- process status;
- system telemetry; and
- approved workspace-file open.

A Stage 10 workstation implementation must preserve:

- signed/expiring command envelopes;
- replay protection;
- explicit capability scopes;
- audit receipts;
- no UAC bypass;
- no unrestricted shell as a default agent primitive; and
- rollback/recovery where meaningful.

## Speech policy

Stage 9 does not claim real microphone/VAD/STT/TTS integration merely because interfaces exist.

On the target PC, Stage 10 must measure:

- capture-to-partial latency;
- capture-to-final latency;
- TTS time-to-first-audio;
- barge-in stop latency;
- CPU/GPU/VRAM impact; and
- interaction degradation while a local LLM is using the GPU.

Priority commands such as `STOP` must continue to use deterministic local control instead of model inference.

## Market-data policy

New Stage 9 paper operations should prefer the consensus path.

A consensus fails closed when:

- fewer than the configured minimum independent sources are fresh;
- a source timestamp is too far in the future;
- source clocks disagree beyond policy; or
- latest prices disagree beyond the configured divergence threshold.

A model cannot override this failure by increasing confidence.

## Backtest policy

Backtests are evaluation evidence, not a promise of profitability.

Stage 9 models:

- fees;
- spread;
- slippage;
- funding;
- walk-forward windows; and
- basic market-regime counts.

Later testnet/paper work should compare predicted behavior with simulated/exchange-testnet fills before any discussion of live execution.

## Paper-trading policy

The Stage 9 paper-open path is intentionally layered:

1. Stage 7/8 analysis produces a signal;
2. owner explicitly accepts the analysis;
3. Stage 9 verifies fresh multi-source market consensus;
4. the existing deterministic Paper Risk Officer checks the trade; and
5. only then may the simulator create a `PAPER_ONLY` position.

No model or agent can skip steps 2–4.

Mark-to-market, correlation and paper equity calculations never create exchange orders.

## Testnet credential policy

Stage 9 has no connected exchange testnet adapter.

If the owner later opts into a Stage 10 testnet connection:

- request newly generated testnet credentials;
- store them through the credential-vault path;
- restrict the key to the minimum required testnet permissions;
- withdrawals/transfers must remain disabled;
- never copy or reuse credentials exposed in earlier chat messages, documentation or logs; and
- audit every simulated/testnet order action.

## Live-money policy

Live-money trading remains `DISABLED` in Stage 9.

Enabling live orders is **not** a configuration flip. It requires a later separately designed stage with explicit owner opt-in, validation evidence, stronger kill switches, exact exchange permissions, independent risk review, testnet history and a separate approval decision.

Withdrawals and transfers should remain outside the AI trading capability even if live order execution is considered later.

## Stage 10 enablement gates

Before a Stage 9 `DISABLED` hardware capability may advance, Stage 10 should require:

- target hardware present and identified;
- least-privilege implementation complete;
- dedicated integration tests;
- rollback/recovery evidence;
- benchmark/resource measurements;
- owner-visible capability status;
- an explicit owner decision when the action class is consequential; and
- a successful full regression run preserving all prior-stage safety tests.
