# Stage 11 — Target Workstation Integration

Stage 11 turns the Stage 10 workstation contracts into a testable Windows-host delivery surface while preserving the deterministic policy, approval, privacy and financial boundaries established in Stages 1–10.

The stage is complete at the repository/CI level when the Linux application regressions, dashboard build and dedicated Windows workstation-agent job are all green. Physical workstation, audio hardware, private-tunnel and external-provider claims remain separate target-environment evidence and are never inferred from CI.

## Delivered components

### Standalone Windows host agent

`workstation-agent/` is a separate Java 21 executable. It is intentionally not a general-purpose remote shell.

The host agent:

- binds to loopback only;
- validates HMAC-SHA256 signed command envelopes;
- binds commands to the configured host identity;
- requires short-lived envelopes and rejects future/expired timestamps;
- persists replay protection and fails closed if replay state cannot be recorded;
- accepts scalar arguments only; and
- exposes only the Stage 10 least-privilege handlers.

Allowed OS actions are limited to:

- `system.telemetry` — read-only system snapshot;
- `process.status` — read-only status for a named process;
- `app.launch` — exact allowlisted executable, without caller-supplied CLI arguments or shell expansion; and
- `workspace.open` — an existing regular file whose real path remains inside an explicitly approved workspace root.

No unrestricted `cmd`, PowerShell, shell, registry-write, scheduled-task, UAC-bypass or arbitrary process-creation primitive is exposed to Syntra/Aetheris.

### Reversible Windows packaging

Stage 11 includes Windows packaging/build/install/uninstall validation scripts. Installation is user-level by default, retains rollback material and stores the host signing key through the CurrentUser DPAPI path rather than ordinary configuration/log payloads.

CI is allowed to build an unsigned source package, but it must remain marked non-production. A package cannot self-promote merely because it was created successfully.

### CurrentUser DPAPI adapter

The orchestrator and host-agent paths include an opt-in Windows CurrentUser DPAPI implementation boundary. Non-Windows CI explicitly proves that DPAPI is not falsely reported as validated.

A physical Windows profile must still execute the DPAPI self-test before the evidence ledger can contain target-workstation proof.

### Watcher → durable memory bridge

Stage 11 connects owner-scoped watcher events to the established Stage 9 incremental-ingestion service.

Rules:

1. the path must be inside an explicitly registered owner watch root;
2. supplied content is SHA-256 hashed before ingestion;
3. the hash must match watcher evidence;
4. unchanged hashes skip unnecessary memory writes;
5. content enters memory through the existing governed `OWNER_FILE` source kind; and
6. deletion evidence becomes a Stage 9 tombstone.

The bridge deliberately reuses the Stage 9 allowlist rather than creating a new ingestion bypass.

### Local speech adapter contracts

Stage 11 adds VAD/STT/TTS adapter configuration with local-only restrictions. Remote model URLs are rejected, STT must support streaming where required, and TTS must support interruption for barge-in behavior.

Configured adapters are not called production-verified until target-PC timing/resource evidence exists.

### Certificate-bound remote sessions

Remote sessions now bind to a SHA-256 device-certificate fingerprint in addition to the existing token, expiry, capability and revocation model.

Remote activation still depends on the Stage 10 private-transport gate:

- TLS 1.3;
- mutual device authentication;
- private network or authenticated tunnel;
- active paired device;
- revocation checked;
- narrow mapped capabilities;
- session credential age <= 60 minutes; and
- no raw public general-purpose agent API.

Passing the software gate means the configuration is eligible for a deployment test; it does not claim that a real tunnel is currently active.

### Durable evidence ledger

Stage 11 persists workstation/runtime/SLO/incident evidence with explicit source semantics. CI and target evidence are not interchangeable.

Examples include:

- `CI_RECORDED:*` — software/regression evidence;
- `TARGET_REPORTED:*` — target-system evidence carrying an attestation hash; and
- pending states where no target/provider proof exists.

An API boolean cannot self-certify target hardware.

## Windows CI gate

The dedicated `workstation-agent` job runs on a Windows GitHub Actions runner and verifies:

1. PowerShell packaging scripts parse successfully;
2. packaging scripts pass policy checks;
3. the Java 21 host agent compiles;
4. the workstation-agent security tests pass;
5. an unsigned Stage 11 package can be produced; and
6. the generated unsigned CI package remains non-production.

Run 294 correctly caught a PowerShell interpolation defect in the packaging validator. The validator was fixed rather than disabled. Run 295 then passed the workstation-agent, dashboard and backend jobs.

## Stage 11 console

`/stage11.html` is an evidence surface, not a secret-entry or deployment-control panel. It shows:

- host package state;
- DPAPI readiness without secret values;
- configured local speech adapters;
- certificate-bound remote sessions;
- durable evidence ledger entries;
- owner watcher roots and watcher events;
- the least-privilege host-agent command surface; and
- testnet readiness while keeping live-money actions visibly disabled.

The console distinguishes CI success from target deployment truth.

## Financial boundary

Stage 11 does not widen trading authority.

- Stage 9 consensus and deterministic Paper Risk Officer remain authoritative.
- Stage 10 richer execution remains `PAPER_SIMULATION_ONLY`.
- Testnet readiness may inspect credential aliases/presence only.
- New owner credentials must enter through the vault.
- Live-money orders remain disabled.
- Withdrawals and transfers remain outside the AI capability.

## External validation still required

Repository/CI completion does **not** claim that the following have already happened on the owner's physical workstation or provider accounts:

- target Windows install and rollback exercise;
- CurrentUser DPAPI self-test under the owner's Windows profile;
- microphone/speaker/VAD/STT/TTS latency under concurrent local-LLM load;
- RTX GPU/VRAM contention and sustained high-refresh desktop profiling;
- real VS Code/application path registration;
- TLS tunnel termination and device-certificate-chain validation;
- production notification delivery;
- redundant external market feeds; or
- real exchange-testnet order/fill/reject/fee evidence.

These require the relevant hardware, network or owner-connected provider and therefore remain explicit target/provider evidence work.

## Stage 12 direction

Stage 12 should build the production-integration fabric needed to consume those external proofs safely: provider registry/health, signed release manifests, target attestation, private-transport adapter boundaries, notification and market-source adapters, testnet-only exchange plumbing and an owner-facing integration console. It must continue to fail closed when credentials, provider connections or target evidence are absent.