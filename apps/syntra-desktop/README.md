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

## Proposed Media Studio staging interface (separate draft PR)

The desktop now has a **Media Studio** navigation entry (`Ctrl+9`) with local-only image/video prompt drafts, frame-size choices and a capped session queue. This is **draft composition, not generation**.

- Media input is kept in the current UI session and can be cleared explicitly.
- Media prompt content is not included in the native diagnostics report or event timeline.
- All backend/API/ComfyUI calls from the media view remain unwired.
- The external AI-runtime repository has a separately reviewed, disabled-by-default Python `media_studio` local ComfyUI image CLI, but the desktop is **not yet connected** to it.
- Video, editing, upscaling, hardware certification and authenticated media storage remain future work.
- `media-studio.contract.json` and `tests/test_syntra_media_studio.py` enforce the no-synthetic-generation boundary.

No paid-provider API calls or GPU access can be started from this desktop view. The original v0.4 diagnostics, local health probes and unsigned packaging continue unchanged.

## Proposed Plugins & Skills research directory (stacked draft)

The draft Syntra Plugins & Skills tab (`Ctrl+0`) adds a premium, accessible, offline-only review browser. It is **not** a plugin installer or marketplace connection.

- 25 packaged static entries: **14 external GitHub research candidates, 5 Aetheris read-only Agent Skills templates and 6 package/model/skills discovery directories**, based on [runtime draft PR #18](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/18).
- Local search across source names, categories and reported licenses; kind and capability filters; responsive result cards; keyboard focus styling; source URL display and user-initiated clipboard copying.
- Per-entry **hypothetical** capability and permission previews. Tool scopes, network access, license approvals, custom code execution, model assets and hardware compatibility are never assumed granted.
- Optional comparison of up to six candidates remains **in the current renderer session only**; there is no backend write, localStorage persistence, credential exchange or external network fetch.
- The exact source snapshot is bundled in `ui/plugins-catalog.js`, not fetched from GitHub. The UI enforces its pinned schema, trusted-source host allowlist, 14+5+6 counts, zero-installed flags and `BLOCKED_PENDING_HARDWARE`. Tampering fails closed.
- All candidate metadata and license labels are **not final legal/security assessments**. In particular, n8n fair-code use and GPL/media checkpoint licenses must be reviewed independently. None of these entries are imported, installed, registered, executed or even connected.

The UI is implemented as `ui/plugins-directory.js` using only safe DOM text nodes, no HTML interpolation, dynamic imports, `eval`, network `fetch`, or Tauri plugin commands. The `plugins-directory.contract.json` safety contract prohibits installation, network access, native secrets and generation claims. It retains the parent branch's Media Studio local-only drafting behavior.

Run review tests on a CI-capable workstation (not required on the owner's unavailable PC):

~~~text
python -m unittest discover -s tests -p 'test_syntra_plugins_directory.py' -v
node --test tests/test_syntra_plugins_directory.cjs
~~~

The dedicated `syntra-plugins-directory.yml` workflow also checks JavaScript syntax and reruns Media Studio regression tests. Because this is a *stacked draft* based on the still-unmerged Media Studio PR #211, mergeability depends on that parent, its verified Windows installer, the independent dependency patch PR #210, security review, and all protected-branch CI. No shortcut merging or production activation is authorized.

## Proposed Creative Studio planning workbench (separate stacked draft)

Syntra has an additional **Creative Studio** workspace (`Ctrl+Shift+C`) with a premium responsive interface and five selectable review disciplines: UI/UX, motion graphics, 3D experience, frontend quality, and creative production. This is **not yet an AI agent, media renderer or design file editor**.

It is a deterministic **offline planning experience**: type a project brief (maximum 600 characters), choose a read-only skill template, and get a structured checklist of recommended review outputs and required evidence. The brief is held in renderer memory only; Clear removes it from the displayed form and checklist. There are no API calls, Tauri invocations, external providers, project writes, model inference, 3D rendering or video output.

It also displays **12 uninstalled design-library candidates** (Three.js, React Three Fiber, Motion, GSAP, Storybook, Tailwind, axe-core, Playwright, Blender, Remotion, Figma plugin samples, ffmpeg.wasm) from [runtime Creative Studio draft PR #20](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/20). They are a frozen local snapshot for research; availability, exact revisions, commercial/asset licenses, security and GPU compatibility are not certified.

Security boundaries are encoded in `creative-studio.contract.json`. CI verifies no elevated capability, the source and skill counts, inert DOM text handling, actual simulated UI events, preservation of Media Studio and Plugins directory, and Windows installer packaging. Use `python -m unittest discover -s tests -p 'test_syntra_creative_studio.py'` and `node --test tests/test_syntra_creative_studio.cjs` to reproduce on CI-capable computers.

This PR must stay **stacked** on the read-only Plugins Directory PR #215, itself stacked on Media Studio PR #211. The actual creative agents, design file editing, plugin installation, image/video generation and owner-PC certification are separate later review and authorization gates. `BLOCKED_PENDING_HARDWARE` remains in force.

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
