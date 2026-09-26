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

The only direct runtime operation remains:

```text
GET http://127.0.0.1:<port>/actuator/health
```

Default port: `8090`. Chat/model inference, task mutation, memory mutation, approvals, live events and remote control execution remain deliberately unwired until their exact authenticated contracts are verified.

## Truth boundaries

- `BLOCKED_PENDING_HARDWARE` remains authoritative.
- CI-generated installers and diagnostics are not physical-PC validation.
- no production activation/deployment is claimed;
- no registry publication is claimed;
- no Windows code-signing certificate is claimed;
- no GPU, microphone, WSL2, Docker, DPAPI/Credential Manager or local-model performance validation is claimed;
- no live-money execution is enabled or claimed.

When the target PC returns, physical validation still starts from Issue #49 and `docs/first-boot-runbook.md`.
