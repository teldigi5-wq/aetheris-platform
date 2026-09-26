# Syntra Desktop v0.3 — Pre-PC Hardening and Installer Readiness

Syntra Desktop is the owner-facing native Windows shell for Aetheris. v0.3 keeps the verified read-only Aetheris health integration from v0.2 and adds the first real Windows installer pipeline plus hardened multi-panel desktop UX.

## Implemented in v0.3

- Tauri package version advanced to `0.3.0`;
- Windows bundling enabled for an **NSIS current-user installer**;
- generated development icon is used for the executable and installer;
- reproducible packaging script builds/tests first, then creates the NSIS bundle;
- CI publishes `Syntra.exe`, `Syntra-Setup-0.3.0-x64.exe`, `SHA256SUMS.txt`, the Cargo lock, and desktop contract;
- real navigable Overview, Chat, Tasks, Memory, Devices, Approvals and Settings views;
- keyboard navigation (`Ctrl+1` through `Ctrl+7`);
- safe loopback-only gateway settings and validated runtime port persistence;
- local prompt staging for UI testing without fabricated model output;
- explicit offline/unverified states for PC, GPU, voice, WSL2/Docker and OS credential storage;
- visible deterministic `STOP`, `PAUSE`, `RESUME` and `TAKE CONTROL` local-intent controls;
- reduced-motion support and keyboard focus treatment.

## Windows development packaging

From a Windows development machine with the pinned Rust toolchain:

```powershell
cd apps\syntra-desktop
.\scripts\package-windows.ps1
```

The script generates the development icon, installs the pinned Tauri CLI if needed, resolves the Cargo lock, executes native tests, builds the NSIS installer, copies outputs to `dist\`, and creates `SHA256SUMS.txt`.

Expected development outputs:

```text
apps\syntra-desktop\dist\Syntra.exe
apps\syntra-desktop\dist\Syntra-Setup-0.3.0-x64.exe
apps\syntra-desktop\dist\SHA256SUMS.txt
```

The installer uses `currentUser` mode so this development package does not require an administrator-wide installation merely to exercise the desktop shell. Code signing is intentionally **not** claimed: CI artifacts remain unsigned development evidence.

## Verified runtime boundary

The only direct runtime operation currently implemented is the read-only loopback health probe:

```text
GET http://127.0.0.1:<port>/actuator/health
```

Default port: `8090`.

The native probe reports healthy only after an actual successful response with actuator status `UP`. The functional desktop API boundary remains `/api/orchestrator`; chat/model inference, task mutation, memory mutation, approvals, live events and remote control execution remain deliberately unwired until their exact authenticated contracts are verified.

## Local settings boundary

The Settings view persists only non-secret local preferences:

- loopback gateway base URL;
- runtime port;
- privacy profile;
- startup behavior.

Gateway configuration rejects non-loopback hosts. This screen does not store API keys, bearer tokens, passwords or provider credentials. Production secrets must eventually use OS-backed storage and still require physical Windows validation.

## Truth boundaries

- `BLOCKED_PENDING_HARDWARE` remains authoritative.
- CI-generated installers are not physical-PC validation.
- no production activation/deployment is claimed;
- no registry publication is claimed;
- no Windows code-signing certificate is claimed;
- no GPU, microphone, WSL2, Docker, DPAPI/Credential Manager or local-model performance validation is claimed;
- no live-money execution is enabled or claimed.

When the target PC returns, the real installer and physical validation sequence must follow Issue #49 and `docs/first-boot-runbook.md` rather than treating GitHub-hosted Windows builds as owner-PC evidence.
