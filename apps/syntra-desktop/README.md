# Syntra Desktop v0.4 — Diagnostics and Recovery

Syntra Desktop is the owner-facing native Windows shell for Aetheris. v0.4 builds on the verified read-only runtime-health and unsigned NSIS installer work by adding a fail-closed recovery center and a native sanitized diagnostics export.

## Implemented in v0.4

- package and installer version advanced to `0.4.0`;
- dedicated Diagnostics view and `Ctrl+8` navigation;
- explicit gateway/runtime states: `NOT_PROBED`, `UP`, `UNAVAILABLE`, `DEGRADED`, `ERROR`;
- recovery check retries only existing read-only local health probes;
- unavailable/degraded components never become synthetic success;
- local chat staging can be cleared without claiming backend-memory deletion;
- prompt contents are removed from event-timeline details;
- native diagnostics export writes `syntra-diagnostics-latest.json` to the current user's temporary directory;
- diagnostics contain only coarse app/configuration/health metadata;
- prompt text, passwords, tokens, API keys and other secret material are explicitly excluded;
- native diagnostics export independently validates loopback gateway configuration and allowlisted state labels;
- Rust unit tests cover loopback validation and diagnostic-label allowlists.

## Sanitized diagnostics schema

The native report records:

- Syntra version and schema version;
- generation timestamp;
- `BLOCKED_PENDING_HARDWARE` physical status;
- backend-command wiring status;
- local deterministic control state;
- loopback gateway base and runtime port;
- coarse gateway/runtime health states;
- non-secret privacy/startup preferences;
- count of locally staged prompts.

It does **not** include prompt content, timeline text, bearer tokens, passwords, provider API keys or other secrets. The native command refuses non-loopback gateway bases and unexpected state labels before writing a file.

## Recovery behavior

`Run recovery check` retries only the read-only gateway/runtime health checks. It does not restart services, mutate the host, execute shell commands, elevate privileges or claim recovery when a component remains unavailable. This makes the recovery UI usable before the target PC exists without crossing the physical validation boundary.

## Authenticated API contract discovery

`authenticated-api.contract.json` records the exact source-verified authentication and orchestrator boundary discovered from platform revision `57ad3e0e602efa6feb275328254151b959a3499b` and certified AI-runtime revision `65a6262717adcd52ac8d8a16ed6f223e299fd74d`.

The verified gateway behavior is:

- `/api/orchestrator/**` requires `Authorization: Bearer <accessToken>`;
- reads require `orchestrator:read`;
- writes require `orchestrator:write`;
- missing/invalid bearer authentication fails with `401`;
- missing required scope fails with `403`;
- client-supplied `X-Aetheris-User-*` identity headers are removed and replaced from verified JWT claims;
- ADMIN and DEVELOPER roles have orchestrator read/write scopes, while API_CONSUMER is read-only.

The contract also records the verified login/refresh/logout DTO fields, deterministic control endpoints, task endpoints, approval decision shape, live/task SSE endpoints, local-model/model-execution endpoints, and the memory boundary.

This is **contract discovery, not backend activation**. No authenticated desktop session, command execution, task mutation, approval decision, model inference, memory mutation, or live-event subscription is wired yet. Refresh/access credentials must not be persisted in browser storage; OS-backed secret storage is required before desktop session wiring.

Memory remains explicitly blocked from desktop wiring because runtime owner-scoped memory APIs require `X-Aetheris-Owner-Id`, while the current gateway synthesizes only `X-Aetheris-User-*` identity headers. Syntra must not guess or forge an owner-ID mapping.

## Source-verified authenticated read preflight (stacked draft)

`auth-read-preflight.cjs` is a dependency-free, pure-data request planner intended to reduce the risk of accidentally wiring unverified API routes before the native credential lifecycle is ready. It validates the source-verified `authenticated-api.contract.json` from the separate contract PR.

Exactly four static **read-only** operations are allowlisted: task list, pending approvals, local-model health, and emergency-stop status. Each descriptor declares its exact gateway-relative path, required `orchestrator:read` scope, and `BLOCKED_NATIVE_CREDENTIAL_BOUNDARY` status. It makes **zero** HTTP requests, never constructs bearer headers, never accepts arbitrary URL inputs, and never includes tokens, login data, or mutable authority flags. Any contract change to methods, scopes, identity header trust, secret storage, source-verified status, or physical-PC truth fails closed. It deliberately excludes memory, live SSE, model generation, task writes, decisions and controls.

This does **not** constitute a logged-in session, successful API call, secure credential storage, agent execution, or integration with the runtime. It is a code-review and test foundation only. Before wiring real reads, implement an independently reviewed native OS-backed credential lifecycle, confirm gateway-issued scopes and response DTOs, and perform physical Windows, CORS/CSRF, expiry/refresh, revocation and failure testing. This module is not imported by the desktop renderer or native process.

Run offline preflight tests with `node --test tests/test_syntra_auth_read_preflight.cjs`. Dedicated GitHub Actions CI exercises adversarial routes and contract drift.

## Native authentication readiness (stacked development draft)

The desktop now has a **Secure AI session** status panel that can ask a new no-argument native Tauri command, `syntra_auth_readiness`, for its actual pre-PC status. It never accepts browser-provided credentials, scopes or readiness flags. The command deliberately returns `BLOCKED_NATIVE_CREDENTIAL_BOUNDARY`, `credentialVaultConnected=false`, `authenticatedSessionActive=false`, `canExecute=false`, and `BLOCKED_PENDING_HARDWARE`.

A dependency-free Rust policy module (`src-tauri/src/auth_readiness.rs`) models the prerequisites for an eventual *read-only* native session: trusted Windows OS-backed vault evidence, signed-session verification, expiry, `orchestrator:read`, gateway identity/authorization evidence, explicit owner consent and hardware certification. Every prerequisite fails closed. Its positive-case unit test is **hypothetical policy evidence only**, not an operational credential or login. The exposed native command **always uses an empty, blocked evidence snapshot**, not the hypothetical test fixture. There is no Tauri login, credential vault, bearer transport, refresh, task mutation or model call.

`ui/auth-readiness-panel.js` displays the native result only after the user presses **Check native readiness**. The UI accepts exclusively the known blocked response; missing native runtime, exceptions, or unexpected / forged responses remain blocked. Native error details and secrets are not rendered. The panel performs no direct HTTP request or persistence.

Validation uses `rustc --edition=2021 --test apps/syntra-desktop/src-tauri/src/auth_readiness.rs`, `node --test tests/test_syntra_native_auth_ui.cjs`, and `python3 -m unittest discover -s tests -p 'test_syntra_native_auth_readiness.py'`, plus a dedicated fast CI workflow and a Windows NSIS shell integration check. Real native OS Credential Manager integration, secure login/refresh/logout, certificate/loopback request trust, revocation, user-consent lifecycle and physical owner-PC testing remain separately reviewed future work. This branch does not install plugins, access paid APIs or update the separate Wayfarer project.

## Proposed native Windows Credential Manager module (isolated draft)

`security-vault/` is an **independent, not-yet-linked Rust crate** for future native-only refresh-token storage using Microsoft's generic Windows Credential Manager APIs. It is not referenced by the Tauri shell, cannot be called through webview IPC, and does not activate login, refresh, model reads, or agents.

- The backend uses `CredWriteW`, `CredReadW`, `CredDeleteW` and `CredFree` for **fixed namespaced targets**, never arbitrary caller-selected credential identifiers or secret enumeration.
- `CRED_PERSIST_SESSION` binds stored credentials to the current Windows logon session (not enterprise roaming or across-logon persistence). Owned Rust secret byte buffers use `zeroize` on drop. Windows-owned read buffers are freed using `CredFree`.
- Oversized, empty or missing values fail closed; errors are coarse without raw OS diagnostics or credential content.
- The credential limit is 2,560 bytes. Only a *future approved native session manager* may use the production refresh-token target; no real bearer token is provisioned in this draft.
- A dedicated Windows CI job builds the isolated crate and uses **synthetic test-only data** in a distinct fixed Credential Manager target, with a cleanup guard. It does not touch the owner's PC or real account.
- The `vault-boundary.contract.json` and Python contract tests enforce nonactivation, zero new Tauri commands and separation from the renderer.

The API documentation is based on Microsoft's WinCred definitions: generic credentials are OS-managed but do **not** automatically provide backend authentication or an access-token lifecycle. Additional work must cover gateway TLS/loopback trust, secret rotation, validated access and refresh responses, memory zeroization coverage, logout/revocation, consent, Windows account boundaries, identity mapping, security review, and real hardware testing. The existing `syntra_auth_readiness` command remains **BLOCKED_NATIVE_CREDENTIAL_BOUNDARY** and `BLOCKED_PENDING_HARDWARE`; this isolated crate must not be described as a successful Windows PC installation.

## Offline Mission Evidence Inspector — source-verified TaskEvent contract (stacked draft)

The Tasks view now includes an **Agent task evidence inspector**. It can ingest up to 500 events from a **user-selected local JSON file (max 256 KiB)** matching the real external runtime's `TaskEvent` record. No socket, token, SSE connection or cloud provider is accessed. The panel opens **disconnected**, and every imported state is marked **OFFLINE IMPORT · UNVERIFIED**. It is an inspector, not a mission executor or a claim that an autonomous agent is working.

The source contract (`mission-evidence.contract.json`) matches these external files at runtime SHA `65a6262717adcd52ac8d8a16ed6f223e299fd74d`:
- `task/TaskEvent.java`: `taskId`, `timestamp`, `state`, `agentId`, `message`, `metadata`;
- `task/TaskState.java`: `QUEUED`, `PLANNING`, `AWAITING_APPROVAL`, `RUNNING`, `VERIFYING`, `PAUSED`, `ROLLING_BACK`, `COMPLETED`, `FAILED`, `CANCELLED`;
- `task/TaskEventStreamService.java`: `task-event` on the aggregate route `GET /api/orchestrator/live/events`.

Only **task ID, timestamp, state and agent ID** survive parsing. `message`, `metadata`, command text, tokens and external instructions are discarded, never rendered or persisted. User-selected files are parsed in-memory, no browser storage is used, and neither the desktop's existing session-only conversation nor control state is changed. The reducer keeps a maximum of 48 task summaries and 96 recent events, rejecting malformed/duplicate/out-of-order timestamps and changes after terminal states.

`node --test tests/test_syntra_mission_evidence.cjs` and `node --test tests/test_syntra_mission_evidence_panel.cjs` cover exact source states, independent task tracking, unverified status, adversarial JSON, size bounds, replay/reorder/terminal protection, refusal to display secrets, and no networking or host calls. The Windows installer workflow also verifies the panel assets and nonactivation contract.

**Next integration boundary:** a separately reviewed native Windows-authenticated agent-session manager must obtain a scoped bearer session using OS-backed credential storage, then consume `task-event` SSE securely with reconnect/history replay, owner/project isolation and strict event provenance. It cannot derive authentication from the reducer, a JSON file, UI state or an arbitrary SSE connection. The current Tauri native command continues reporting `BLOCKED_NATIVE_CREDENTIAL_BOUNDARY`; the owner workstation remains `BLOCKED_PENDING_HARDWARE`.

## Native TaskEvent Stream Decoder (isolated; NOT CONNECTED)

`mission-stream/` is a standalone Rust SSE framing/parser crate for the external Aetheris runtime, **not** a native authenticated session, an HTTP client or a Tauri IPC command. It is deliberately not linked into `src-tauri` and therefore cannot read an owner's tasks on its own. The source contract is `teldigi5-wq/aetheris-ai-runtime@65a6262717adcd52ac8d8a16ed6f223e299fd74d` `TaskEventStreamService.java`: the aggregate SSE route emits an `event: task-event`, `id: <taskId>:<Instant>` and a JSON `data:` body.

The decoder:
- handles bounded incremental LF and CRLF frames, including fragmented packets and SSE comment keepalives;
- requires a valid UUID, Java UTC `Instant`, one of ten source-defined states, a bounded optional agent identifier and an SSE ID exactly matching the task/timestamp;
- borrows only expected JSON fields; does **not** materialize message, metadata or potential token payloads as returned data;
- refuses malformed or oversized frames and caps in-memory replay IDs at 1,024;
- gives every parsed result the explicit `UnauthenticatedBytes` provenance. Parsing does not imply a verified source, authenticated owner, successful execution or safe UI display.

Run tests: `cargo test` in `apps/syntra-desktop/mission-stream` plus `python3 -m unittest discover -s tests -p 'test_syntra_native_mission_stream_contract.py'`. Dedicated GitHub Actions executes parser tests on Ubuntu and Windows. The existing offline JSON inspector and voice UI remain unchanged in behavior.

**Activation blocker:** a separately reviewed native session manager must connect Windows OS-backed credentials, verify the JWT and gateway/owner scope, enforce loopback identity, protect against replay across reconnects, and provide secure user/project isolation before connecting a real SSE client. `syntra_auth_readiness` still reports `BLOCKED_NATIVE_CREDENTIAL_BOUNDARY` and the actual owner PC is `BLOCKED_PENDING_HARDWARE`. This decoder is tested against synthetic source-format event payloads, not live mission traffic.

## Windows development packaging

```powershell
cd apps\syntra-desktop
.\scripts\package-windows.ps1
```

Expected development outputs:

```text
apps\syntra-desktop\dist\Syntra.exe
apps\syntra-desktop\dist\Syntra-Setup-0.4.0-x64.exe
apps\syntra-desktop\dist\SHA256SUMS.txt
```

The installer remains unsigned, current-user development evidence only.

## Verified runtime boundary

The only direct runtime operation currently performed by the desktop remains:

```text
GET http://127.0.0.1:<port>/actuator/health
```

Default port: `8090`. The authenticated API contract is now source-verified, but chat/model inference, task mutation, memory mutation, approvals, live events and remote control execution remain deliberately unwired until their desktop authentication, credential-storage and ownership boundaries are implemented and verified without weakening existing controls.

## Truth boundaries

- `BLOCKED_PENDING_HARDWARE` remains authoritative.
- CI-generated installers and diagnostics are not physical-PC validation.
- no production activation/deployment is claimed;
- no registry publication is claimed;
- no Windows code-signing certificate is claimed;
- no GPU, microphone, WSL2, Docker, DPAPI/Credential Manager or local-model performance validation is claimed;
- no live-money execution is enabled or claimed.

When the target PC returns, physical validation still starts from Issue #49 and `docs/first-boot-runbook.md`.
