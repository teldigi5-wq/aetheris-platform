# Stage 8 Production Capability Policy

This document is the capability truth table for Syntra + Aetheris at the end of Stage 8. It exists to prevent planned contracts from being mistaken for production-enabled capabilities.

## Capability states

- **ENABLED** — implemented and covered by current CI/integration evidence.
- **SIMULATION** — implemented only as a non-production simulation/dry-run path.
- **CONTRACT_ONLY** — interface/protocol exists, but no real provider/hardware adapter is connected.
- **APPROVAL_GATED** — action requires an exact owner approval and may still require a connected adapter.
- **PAPER_ONLY** — financial operation exists only in simulated paper state.
- **HARDWARE_GATED** — implementation is intentionally deferred until the actual target workstation is available.
- **DISABLED** — must not execute in this stage.

## Intelligence and orchestration

| Capability | Stage 8 state | Notes |
|---|---|---|
| Durable task/mission control | ENABLED | Governed by existing task states, owner rules, approvals and emergency stop. |
| Priority scheduler/worker leases | ENABLED | Stage 7 scheduler remains authoritative. |
| Autonomous dispatcher coordinator | ENABLED | Uses existing leases/concurrency/backpressure; does not bypass scheduler governance. |
| Model routing/fallback | ENABLED | Private/Zero-Cost/budget/rate-limit restrictions remain hard filters. |
| LLM mission-plan proposal | ENABLED | Proposal only; deterministic parser/DAG/safety validation runs before optional materialization. |
| Arbitrary model-created execution | DISABLED | Models do not get a direct execution bypass. |
| Evaluation/regression evidence | ENABLED | Deterministic durable scoring supports comparison; not a sole safety oracle. |

## Memory and data

| Capability | Stage 8 state | Notes |
|---|---|---|
| Knowledge graph | ENABLED | Stage 7 durable memory. |
| Local vector index | ENABLED | Initial zero-cost local hash embedding adapter with cosine search. |
| Lexical fallback | ENABLED | Deterministic fallback remains available. |
| Protected-memory filtering | ENABLED | Protected content excluded unless explicitly requested by an authorized caller. |
| Repository/document/owner-file text ingestion | ENABLED | Explicit selected content only. |
| Silent workstation crawling | DISABLED | No automatic scan of arbitrary personal files. |
| True neural local embedding model | CONTRACT_ONLY | Adapter boundary exists; stronger local model is a future replacement. |

## Voice

| Capability | Stage 8 state | Notes |
|---|---|---|
| Voice session state | ENABLED | Durable Stage 7 session contracts. |
| Deterministic STOP/PAUSE/RESUME/TAKE_CONTROL | ENABLED | Does not depend on model inference. |
| STT adapter interface | CONTRACT_ONLY | No microphone/STT implementation claimed. |
| TTS adapter interface | CONTRACT_ONLY | No production speaker/TTS implementation claimed. |
| Voice latency/barge-in evidence | ENABLED | Metrics can be recorded and evaluated. |
| Always-listening microphone | DISABLED | Requires explicit future privacy/runtime design. |

## Career and public profile

| Capability | Stage 8 state | Notes |
|---|---|---|
| Career-readiness audit | ENABLED | Evidence supplied to Stage 7 audit service. |
| Recruiter simulation | ENABLED | Produces screening score/verdict from available evidence. |
| Read-only career connector interface | CONTRACT_ONLY | Connector must report read-only capability and availability. |
| LinkedIn/public-profile automatic edits | DISABLED | Public identity changes require supported integrations and owner approval. |
| Automatic public posting | DISABLED | Not part of Stage 8. |

## Notifications and remote companion

| Capability | Stage 8 state | Notes |
|---|---|---|
| In-app notifications | ENABLED | Existing durable notification center. |
| Desktop/mobile/email delivery contract | APPROVAL_GATED | Exact per-task/channel approval required. |
| Real desktop/mobile/email provider | CONTRACT_ONLY | Returns `ADAPTER_NOT_CONNECTED` until a real adapter exists. |
| Remote companion pairing token | ENABLED | Random one-time token returned; SHA-256 hash persisted. |
| Remote session expiry/revocation | ENABLED | Capability-scoped and revocable. |
| Production private network transport | CONTRACT_ONLY | No claim of deployed VPN/mTLS/WebSocket transport. |
| Raw public remote agent API | DISABLED | Must not expose uncontrolled agent execution. |

## Windows / workstation

| Capability | Stage 8 state | Notes |
|---|---|---|
| Host identity/pairing/revocation | ENABLED | Stage 6 cryptographic host trust. |
| Signed host command envelope | SIMULATION | Verified simulation path only. |
| Replay-protected command acknowledgement | SIMULATION | Stage 7 host-daemon protocol evidence. |
| Windows application launch/control | HARDWARE_GATED | No production host executor is installed. |
| Driver/registry/BIOS/firmware changes | DISABLED | Requires actual workstation, dedicated handlers, backups and owner approval. |
| Windows Credential Manager/DPAPI vault | HARDWARE_GATED | Interface/plan exists; not falsely marked implemented. |

## Financial / trading

| Capability | Stage 8 state | Notes |
|---|---|---|
| Market-analysis signals | ENABLED | Analysis only; no profit guarantee. |
| Owner acceptance of analysis | ENABLED | Acceptance is not execution. |
| Manual/synthetic market-data adapter | ENABLED | Read-only source used for development/tests. |
| Stale-data fail-closed policy | ENABLED | Paper actions reject data older than configured maximum age. |
| Backtesting with train/test evidence | ENABLED | Baseline moving-average research harness. |
| Deterministic Risk Officer | ENABLED | Enforces acceptance, stop, leverage, R:R, daily-loss and exposure controls. |
| Paper account/positions/journal | PAPER_ONLY | Simulated fills and P&L. |
| Paper/testnet exchange connector | CONTRACT_ONLY | Not connected in Stage 8. |
| Live broker/exchange orders | DISABLED | No endpoint/adapter is implemented. |
| Withdrawal/transfer capability | DISABLED | Must remain absent. |
| Profit guarantee | DISABLED | No system can legitimately guarantee trading profits. |

## Enabling rule for future stages

A capability may move from `CONTRACT_ONLY`, `SIMULATION`, `PAPER_ONLY` or `HARDWARE_GATED` to production only after all of the following are true:

1. the real adapter/handler exists;
2. its permissions are least-privilege and credential storage uses the approved vault;
3. deterministic owner rules and risk limits are defined;
4. destructive/durable/public/financial actions have an explicit approval policy;
5. unit/integration/failure-path tests pass;
6. rollback/revocation/emergency-stop behavior is defined where applicable;
7. observability/audit evidence exists;
8. the target hardware/provider has been tested when hardware/provider behavior matters; and
9. the owner explicitly enables the capability when the action can create durable external consequences.

## Stage 8 non-negotiable boundaries

- No hidden paid-model fallback in Zero-Cost mode.
- No hidden remote-provider fallback for protected data in Private mode.
- No model bypass of deterministic planner/risk validation.
- No unrestricted shell execution.
- No fake notification delivery.
- No fake STT/TTS or Windows hardware integration.
- No live-money execution.
- No withdrawals or transfers.
- No real Windows administrative action before the target PC is available and separately validated.
