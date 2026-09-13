# Stage 10 — Workstation Deployment & Reliability

Stage 10 moves Syntra + Aetheris from workstation **contracts** toward a deployable, evidence-gated runtime while preserving every Stage 1–9 safety boundary.

The central rule is hardware honesty: CI can prove command guards, scheduling logic, policy gates, order simulation and API behavior, but it cannot prove that the owner's physical Windows workstation, microphone, GPU, DPAPI vault or private remote tunnel are functioning. Those capabilities remain explicitly `HARDWARE_PENDING` or deployment-test gated until measured on the target PC.

## Stage 10 goals

1. define a reversible target-PC bootstrap and rollback lifecycle;
2. constrain Windows actions to least-privilege handlers rather than an unrestricted shell;
3. protect voice/UI responsiveness while local model workloads use CPU/GPU/RAM/VRAM;
4. require target-hardware evidence before calling speech or DPAPI production-ready;
5. restrict file/repository watchers to owner-selected roots and hash-based change evidence;
6. require authenticated encrypted private transport for remote operation;
7. block model/agent/runtime promotion when benchmark or safety regressions occur;
8. model richer paper order behavior without creating a live exchange path;
9. inspect testnet credential readiness without exposing secret values; and
10. provide owner-facing SLO, recovery and update-channel policy evidence.

## Runtime components

### `Stage10WorkstationService`

Provides the workstation bootstrap plan, readiness probe, least-privilege command validation, resource governor, speech benchmark evaluation and OS-vault readiness.

Initial Windows capabilities are intentionally narrow:

- `system.telemetry` / `snapshot`;
- `process.status` / `status`;
- `app.launch` / `launch`, restricted to configured application aliases; and
- `workspace.open` / `open`, restricted to configured owner workspace roots.

The guard rejects arbitrary command-line arguments for app launch and explicit shell/admin patterns such as PowerShell, `cmd.exe`, DiskPart, formatting, registry writes, `runas`, scheduled-task creation, process-creation via WMIC and bypass-oriented commands.

It does **not** bypass UAC, expose a generic shell, or mark Windows execution validated simply because an API endpoint exists.

### Bootstrap lifecycle

The Stage 10 bootstrap contract uses this sequence:

1. verify the Stage 10 host package/checksum;
2. install only into an owner-selected application directory;
3. register the host agent with least privilege;
4. pair the host through the existing challenge/response protocol;
5. grant only the initial capability allowlist; and
6. complete health checks before enabling automatic startup.

Rollback must be able to stop/disable the service, revoke host identity, remove/replace the package while retaining audit and owner data, and restore a previous approved package when requested.

The repository does **not** claim a Windows installer or service has already run on the target machine.

## Resource-aware local runtime

The Stage 10 governor protects interaction quality instead of maximizing inference throughput blindly.

Hard behavior:

- deterministic `PRIORITY_CONTROL` (`STOP`, `PAUSE`, `TAKE_CONTROL`) is allowed even under severe resource pressure;
- heavy local LLM, embedding rebuild, benchmark and backtest batches are deferred while speech is active;
- heavy work may be deferred when interactive UI load and CPU pressure are high;
- additional local-model workloads are deferred when VRAM headroom is low; and
- critical CPU/RAM/VRAM pressure can reject or defer non-priority work.

This is the software policy layer. Actual tuning thresholds should be measured and refined on the target workstation.

## Speech validation

Speech is not promoted merely because STT/TTS interfaces exist.

Stage 10 benchmark targets are:

| Metric | Initial target |
|---|---:|
| capture → partial transcript | <= 450 ms |
| capture → final transcript | <= 1200 ms |
| TTS first audio | <= 500 ms |
| barge-in stop | <= 250 ms |
| CPU during benchmark | <= 90% |
| GPU during benchmark | <= 97% |
| VRAM during benchmark | <= 95% |

A sample without `measuredOnTargetHardware=true` returns `EVIDENCE_REQUIRED`, even when all numeric timings look excellent.

## OS-backed credential vault

`CredentialVault` remains the generic secret-resolution boundary. Stage 10 separately checks whether an `OperatingSystemCredentialVault` is active and reports whether it claims OS/hardware backing.

CI/environment vaults do not count as proof of Windows Credential Manager/DPAPI integration.

Until a real implementation is installed and tested on the owner workstation, vault status remains `HARDWARE_PENDING`.

Secrets must never be returned by readiness APIs, task logs, trade evidence, dashboards or audit payloads.

## Owner-scoped file watchers

`Stage10WatcherService` records hash-based change events only for explicit owner roots.

It rejects broad/high-risk roots such as:

- filesystem/drive root;
- a Windows user-home root;
- `C:/Windows`;
- `C:/ProgramData`; and
- AppData as a root.

Events outside the configured root are rejected. Parent traversal is rejected. Only configured safe extensions are accepted.

The service records `ADDED`, `UNCHANGED`, `CHANGED` and `DELETED` evidence based on SHA-256 hashes. It does not recursively crawl owner disks or inspect unrelated file content merely because it exists.

The current Stage 10 implementation stores watcher registration/evidence in the runtime process; a later host bridge can make filesystem event delivery durable without weakening root scoping.

## Private remote transport gate

Stage 10 adds a fail-closed production-readiness validator. A remote transport is not considered ready unless evidence shows:

- TLS 1.3;
- mutual device authentication;
- a private network or authenticated tunnel;
- active paired-device identity;
- revocation state checked;
- narrow explicit capabilities;
- short-lived session credentials (<= 60 minutes); and
- no raw public general-purpose agent API.

Passing this policy returns `READY_FOR_DEPLOYMENT_TEST`; it does **not** claim a tunnel has been deployed.

## Regression promotion gate

Before a candidate agent/model/runtime change is promoted, Stage 10 can require:

- functional/integration tests pass;
- candidate benchmark score remains >= the established PASS threshold of 65;
- score regression stays within an explicitly configured allowance;
- hallucination penalty stays within policy; and
- critical safety failures remain zero.

A passing benchmark never grants new capabilities or bypasses normal owner approvals.

## Richer paper-order simulation

`Stage10PaperOrderSimulatorService` adds simulation evidence for:

- MARKET;
- LIMIT;
- STOP;
- STOP_LIMIT;
- partial fills;
- fees;
- slippage; and
- funding.

The simulator requires Stage 9 multi-source market consensus and still calls the deterministic Paper Risk Officer. An unaccepted signal therefore remains rejected.

The simulator deliberately does **not** create a durable position. It returns `PAPER_SIMULATION_ONLY` evidence with `exchangeOrderSent=false`, `liveMoneyAllowed=false` and `withdrawalsAllowed=false`.

The established Stage 9 position-opening path remains authoritative:

`accepted analysis -> market consensus -> deterministic Paper Risk Officer -> PAPER_ONLY position`

This avoids accidentally turning a richer simulation model into a second execution authority.

## Testnet readiness

`Stage10TestnetReadinessService` checks only whether the configured credential aliases are present and whether a testnet adapter is enabled.

Possible states include:

- `CREDENTIALS_MISSING`;
- `CREDENTIALS_PRESENT_ADAPTER_DISABLED`; and
- `READY_FOR_TESTNET_CONNECTIVITY_VALIDATION`.

Readiness output never includes credential values.

Credential policy is fixed to `NEW_OWNER_CREDENTIALS_VIA_VAULT_ONLY`: credentials exposed in earlier chats, logs or documentation must never be reused.

Even when a future testnet adapter is enabled:

- live money remains disabled;
- withdrawals remain disabled; and
- transfers remain disabled.

## SLO and recovery evidence

`Stage10OperationsService` defines initial runtime targets:

- command acknowledgement <= 300 ms;
- priority control <= 250 ms;
- mission-event delivery <= 500 ms;
- host heartbeat age <= 30 s;
- oldest ready queue item <= 120 s;
- runtime error rate <= 1%; and
- memory pressure <= 92%.

As with speech, synthetic/unmeasured data returns `EVIDENCE_REQUIRED` rather than `HEALTHY`.

The recovery runbook begins with deterministic `STOP ALL`, captures evidence, revokes compromised remote/host identities when necessary, restores an owner-approved package/checkpoint, and re-enters simulation/observe mode before automation is re-enabled.

Recovery must never delete owner projects, discard audit evidence to hide an incident, weaken UAC/firewall/vault/policy controls, or enable live-money execution.

## Installer/update policy

Stage 10 defines a signed-canary update contract requiring:

- package checksum;
- publisher/signature verification;
- version provenance;
- rollback package; and
- pre-update safety-regression evidence.

Rollout is canary-first and must retain the previous package for rollback.

The current status explicitly says `installerProduced=false`; packaging/signing and execution on the real workstation are later deployment steps.

## Stage 10 APIs

Runtime endpoints live under `/api/orchestrator/stage10` and cover:

- bootstrap/readiness;
- guarded workstation command validation/issue;
- resource governance;
- speech benchmarks;
- vault readiness;
- private-transport validation;
- owner watch roots and events;
- regression promotion gates;
- SLO assessment;
- recovery runbook; and
- signed-update policy.

Trading endpoints under `/api/orchestrator/stage10/trading` cover richer paper-order simulation and testnet readiness.

## Console

`/stage10.html` is the Stage 10 evidence console. It keeps four boundaries visible:

- `LIVE MONEY · DISABLED`;
- `WINDOWS / DPAPI / SPEECH · HARDWARE PENDING`;
- `REMOTE TRANSPORT · POLICY GATED`; and
- `STAGE 9 SAFETY CONTRACTS · PRESERVED`.

API-derived text is escaped before HTML rendering.

## What Stage 10 does not claim

Stage 10 does not claim that:

- a Windows host service is currently installed;
- DPAPI/Credential Manager has been validated on the physical PC;
- a microphone/VAD/STT/TTS chain has been benchmarked on the physical PC;
- a private remote tunnel is deployed;
- a production notification provider is connected;
- a real exchange testnet adapter is connected unless the readiness evidence later proves it; or
- live trading, withdrawals or transfers exist.

Those distinctions are intentional quality requirements, not missing labels.

## Stage 11 candidates

A later Stage 11 should focus on real target-environment evidence and durable delivery:

1. run/package the Windows host agent on the actual workstation and validate the least-privilege handlers;
2. implement and validate Windows Credential Manager/DPAPI secret storage;
3. connect real local VAD/STT/TTS engines and record target-PC latency/resource benchmarks;
4. implement the mutually authenticated private remote transport and exercise pairing/revocation/recovery;
5. bridge owner-root filesystem events into durable incremental ingestion state;
6. build a signed installer/update channel with canary rollback evidence;
7. strengthen desktop-shell performance/crash recovery and package it for Windows;
8. connect redundant read-only market sources with durable source-health telemetry;
9. add production notifications only after the owner connects a legitimate provider and grants the required action class;
10. optionally connect a **testnet-only** exchange adapter using newly generated vault credentials and minimum permissions; and
11. keep live-money execution disabled unless a later separately designed stage explicitly opts in with independent risk review and owner approval.
