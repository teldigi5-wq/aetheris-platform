# Syntra Desktop v0.1 Foundation

Syntra Desktop is now moving from a contract-only placeholder into a real native Windows application foundation.

This directory contains a **Tauri 2 native shell**, a first owner-facing command-center UI, deterministic local control intents, local gateway probing, and a Windows GitHub Actions build that can produce an **unsigned development `Syntra.exe`** before the owner PC is available.

The desktop still consumes the authenticated Aetheris gateway boundary rather than duplicating runtime source. The orchestrator behind `/api/orchestrator` remains owned by the external `teldigi5-wq/aetheris-ai-runtime` repository.

## Current v0.1 slice

Implemented now:

- native Tauri 2 Windows shell;
- premium dark command-center UI;
- Overview, Chat, Tasks, Memory, Devices, Approvals and Settings navigation shell;
- visible `STOP`, `PAUSE`, `RESUME` and `TAKE CONTROL` controls;
- local-only control state with explicit no-success-without-evidence wording;
- system-health panel and local gateway health probing;
- live event timeline shell;
- truth-boundary banner showing physical validation is still pending;
- Windows CI build for an unsigned development executable.

The UI source is under `ui/`. The native Rust/Tauri host is under `src-tauri/`.

## Build locally later

On a Windows machine with the pinned Rust toolchain available:

```powershell
cd apps\syntra-desktop\src-tauri
cargo generate-lockfile
cargo build --locked --release
```

Expected executable:

```text
apps\syntra-desktop\src-tauri\target\release\Syntra.exe
```

The GitHub workflow `.github/workflows/syntra-desktop-build.yml` performs the equivalent Windows build in CI and publishes the unsigned development executable as an artifact.

## Desktop contract

The shell contract remains authoritative for the platform-facing boundary:

- API base: `/api/orchestrator`
- live stream: `/api/orchestrator/live/events`
- controls: `STOP`, `PAUSE`, `RESUME`, `TAKE_CONTROL`, `APPROVE`, `REJECT`
- host pairing: challenge/response
- host execution default: simulation-only
- production secret storage: OS-backed

The current UI does **not** invent backend success. Control buttons update only local desktop intent state until the authenticated runtime control endpoint is deliberately wired and tested.

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
