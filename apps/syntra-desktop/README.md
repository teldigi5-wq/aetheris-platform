# Syntra Desktop v0.2 — Aetheris Health Integration

Syntra Desktop is a native Tauri 2 Windows application foundation for the owner-facing Aetheris interface. v0.2 adds the first deliberately verified runtime integration: a **read-only native health probe** against the local Aetheris orchestrator actuator endpoint.

The functional desktop boundary still consumes authenticated Aetheris gateway APIs rather than bypassing the gateway for chat, task, memory, approval or control execution. The v0.2 runtime probe is health evidence only and is restricted to loopback.

## Current v0.2 slice

Implemented now:

- all v0.1 native Windows shell and command-center UI behavior;
- separate local gateway health probing;
- native Rust probe for `GET http://127.0.0.1:<port>/actuator/health`;
- default runtime port `8090`, matching the verified runtime configuration;
- three-second native probe timeout with explicit reachable/healthy distinction;
- actuator `status` evidence surfaced in the System health panel and local timeline;
- deterministic unit tests proving the probe stays on loopback and only treats successful `UP` responses as healthy;
- visible `STOP`, `PAUSE`, `RESUME` and `TAKE_CONTROL` controls that remain local-only;
- Windows CI build for an unsigned development executable.

The native Rust/Tauri host is under `src-tauri/`. The UI source is under `ui/`.

## Verified runtime contract used by this slice

The current `teldigi5-wq/aetheris-ai-runtime` orchestrator configuration exposes Spring Boot actuator `health`, `info` and `prometheus` endpoints and defaults the orchestrator port to `8090`. Its orchestrator module includes `spring-boot-starter-actuator` and does not include Spring Security. v0.2 therefore sends no credentials and uses only the read-only health route.

Syntra does **not** infer health from repository state. The UI changes to `UP` only when the running local endpoint returns a successful HTTP response with actuator status `UP`. A non-success response, a non-`UP` status, malformed JSON, timeout, or connection failure remains visibly non-healthy/unavailable.

The probe host is hard-coded to `127.0.0.1` in native Rust. The UI may choose a local port, but it cannot use this command to probe arbitrary remote hosts.

## What remains deliberately unwired

This slice does not wire:

- chat or model inference requests;
- task creation or mutation;
- memory reads/writes;
- approvals;
- `/api/orchestrator/live/events`;
- remote `STOP`, `PAUSE`, `RESUME` or `TAKE_CONTROL` execution.

Those surfaces must be integrated only after their exact route, authentication, payload, authorization and failure contracts are verified.

## Build locally later

On a Windows machine with the pinned Rust toolchain available, generate the deterministic development icon and build:

```powershell
cd apps\syntra-desktop
.\scripts\generate-dev-icon.ps1
cd src-tauri
cargo generate-lockfile
cargo test --locked
cargo build --locked --release
```

Expected executable:

```text
apps\syntra-desktop\src-tauri\target\release\Syntra.exe
```

The generated icon is development-only and is intentionally not treated as final Syntra branding. GitHub Actions performs the same preparation, tests and Windows build, then publishes the unsigned development executable as an artifact.

## Desktop contract

The shell contract remains authoritative for the platform-facing boundary:

- API base: `/api/orchestrator`;
- live stream: `/api/orchestrator/live/events`;
- runtime health: loopback-only `GET /actuator/health`, default port `8090`;
- controls: `STOP`, `PAUSE`, `RESUME`, `TAKE_CONTROL`, `APPROVE`, `REJECT`;
- host pairing: challenge/response;
- host execution default: simulation-only;
- production secret storage: OS-backed.

The current UI does **not** invent backend success. The new health integration is read-only. Control buttons update only local desktop intent state until an authenticated control endpoint is deliberately wired and tested.

## Safety and truth boundaries

Hard requirements remain:

- no embedded API secrets;
- no generic shell capability;
- emergency controls must not depend on model inference;
- high-risk host actions require owner policy/approval;
- GPU-heavy effects must yield to inference and interaction responsiveness;
- runtime/provider/tool/task/approval/host state must be visible rather than implied;
- local APIs must not be exposed directly to the public internet;
- live-money execution remains outside the trusted default authority.

The CI-produced executable is **unsigned development evidence only**. It is not a production-certified Windows installation.

**Physical-machine status remains `BLOCKED_PENDING_HARDWARE`.** Final microphone, GPU, WSL2, Docker, DPAPI/Credential Manager, local-model performance, startup, thermal and end-to-end validation still require the real target PC.
