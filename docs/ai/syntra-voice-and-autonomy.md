# Syntra × Aetheris — Voice, Autonomous Work and Personal Executive System

**Owner directive:** voice-first private companion, secretary, engineering operator and CEO adviser, sharing one governed local-first Aetheris task platform. Existing master blueprint and hard owner rules remain authoritative.

**Evidence state (2026-10-13):** offline text-to-intent parser plus **opt-in local-system TTS output panel** in a stacked draft. The TTS path is a real WebView speechSynthesis call gated to voices advertising `localService=true`, with explicit Speak/Stop controls; its UI behaviors are tested using stubs. Actual audio playback has **not been measured on the owner's PC**. Microphone capture, audio wake-word detection, STT, full-duplex speech, app launching, independent business operation and continuous learning are **not** activated. Physical owner PC status: `BLOCKED_PENDING_HARDWARE`.

## Desired experience

- Opt-in microphone can detect **“Hi Sintra”, “Hi Syntra”, “Hey Sintra”, “Hey Syntra”, “Sintra”, or “Syntra”** using local wake-word detection, independent of remote services; keyboard and manual activation remain available. Short acknowledgement and an obvious listening/speaking/processing indicator. Custom wake enrollment requires explicit consent and real-device validation.
- Continuous, low-latency, full-duplex conversational experience: streaming local VAD + STT, streaming voice synthesis (TTS), barge-in interruption, push-to-talk and pause/mute, speaker authorization for actions, manual volume/output device controls, wake lockout on speech playback, acoustic echo cancellation and no raw microphone-audio retention by default.
- Syntra should **keep communicating while existing governed tasks run**. The speech loop is separate from the task queue; tasks have independently persisted IDs, leases, progress, checkpoints, cancellation, retries, resource budgets, deadlines, evidence, and observable owner controls. Never treat narration as completed work.
- **Computer use:** launch explicitly approved applications, interact through official APIs where possible, OS accessibility UIA when appropriate, browser automation when approved, and visual clicking only as last resort. Launch is not the same as approval to read documents, access credentials, type arbitrary shell commands, send messages or purchase anything.
- **Professional roles:** companion, secretary, chief of staff, engineering lead, planner, research analyst, tutor, creative director, cybersecurity analyst, project manager and CEO adviser. Roles change advice, delegation and response presentation, not security authority. Owner retains final control; Syntra cannot legally replace an accountable human company director.
- **Sandbox and testing:** isolated execution workspace, allowlisted capabilities, default-deny network, filesystem and process access, capped CPU/RAM/VRAM/storage, per-task snapshots and rollback, disposable preview/browser environments, test-before-deploy policy, audit and human approvals. Destructive, financial, account/admin, external-send, model/agent update and publish actions require explicit authorization.
- **Private storage:** separate encrypted owner profile, project-by-project memory, temporary session cache, retrieval corpus, task logs, artifact store, test fixtures and backups, with per-scope retention and owner deletion/export. Never copy credentials, private records or cross-project content into training data by default.
- **Daily improvement:** opt-in collection of high-quality experiences, citations/source provenance, searchable local knowledge retrieval, scheduled authorized research/updates, re-evaluation of outcomes, feedback and benchmarks. New memories have retention and confidence metadata and can be corrected/deleted. Model-weight tuning, self-updates and installed skills require provenance checks, benchmark regressions, sandbox tests and owner approval. **No unrestricted self-modification or claim of infinite learning.**
- **Reliability:** bounded concurrency, watchdogs, timeout and backpressure, crash recovery, network/provider circuit breakers, low-memory fallbacks, health/status truth, signed update staging, deterministic rollback, safe startup and emergency STOP that revokes leases/temporary capabilities. No software can be guaranteed never to crash.
- **Privacy-first:** no always-on microphone unless the owner actively enables it; hardware/software mute, visible capture state, no hidden screen monitoring, redaction, minimize raw audio retention, configurable device-only processing and clear notices for anything leaving the workstation. Spoken instructions are untrusted until identity/context, existing policy, capability grants and risk gates are checked.

## End-to-end execution boundary

```text
Owner microphone (explicit opt-in) or push-to-talk
  -> local wake engine / VAD / echo control
  -> local streaming STT (untrusted speech transcript)
  -> source + voice-session authorization and intent preflight
  -> existing Aetheris signed owner/session and policy engine
  -> local model/specialist planner (when actually available)
  -> scoped tools + risk approval + isolated sandbox + tests
  -> workstation/browser/app adapter on granted device
  -> verified task events / durable artifacts / progress
  -> Syntra streaming TTS + captions + interruptible dialogue
  -> opt-in and auditable owner/project memory
```

**The spoken phrase “open VS Code” must not directly become `subprocess` / `cmd.exe` / `powershell.exe` execution.** The preflight proposes `app.launch.vscode`; only a future least-privilege, allowlisted Windows host adapter with exact project/owner grants can launch the app. Real desktop actions require a verified live auth session; wake-phrase recognition alone is not speaker authentication.

Example flows, once connectors and adapters are independently proven:
1. “Hi Sintra” → verified local wake event; reply “Listening.”
2. “Open VS Code and work on Aetheris while we discuss tomorrow” → request scoped application launch; ask for the relevant repo/workspace if necessary, stage a separate engineer task, notify when tests start/finish while conversation stays interruptible.
3. “Act as my secretary and prepare tomorrow’s schedule” → read approved calendar context, propose a schedule with source evidence, never fabricate calendar access.
4. “Run my company as CEO” → begin a **CEO advisory/operations workflow**, reporting proposals, priorities and risks. Contracts, payments, production changes, hiring, public statements and legal decisions remain human approved.
5. “Learn what changed today” → retrieve approved sources, summarize candidates, let owner review durable additions. Do not silently rewrite the model, install code, scrape private accounts or claim new knowledge without provenance.
6. “Test changes first” → sandbox worktree / simulated environment; independent QA and approval gates before staging a real deployment.

## Deliverable stages and acceptance criteria

| Stage | Acceptance criteria | Current evidence |
| --- | --- | --- |
| A — spoken-text command gateway | Six wake spelling/phrase aliases; consent and trusted-capture guard; exact application allowlist; emergency control intent; no execution | This draft; Node CI only |
| B — local voice device adapters | Open-source license-reviewed wake model + VAD + streaming STT + TTS, privacy LEDs and explicit mute; microphone/speaker hardware tests | Opt-in system TTS UI with local-voice restriction and CI; wake/STT/hardware validation pending |
| C — full-duplex and work queue | Conversation/TTS interruptible while tasks run with durable pause/resume/progress receipts and strict resource budgets | Existing runtime task infrastructure, audio bridge not wired |
| D — desktop computer use | Win32 host app allowlist, Windows UIA/browser integration, sandboxed approvals and actual app-open evidence | Not activated |
| E — local owner/private memory | Encrypted project isolation, retention, retrieval and reviewable day-by-day knowledge updates | Architecture exists; integrated desktop behavior not proven |
| F — executive specialist teams | Approved calendar/mail/project integrations, cross-agent verification, daily executive brief, cost/accounting controls | Executive synthetic/hosted proofs; live connectors vary |
| G — self-improvement via evidence | Signed model/plugin update review, benchmark suites, regression gates, rollback and safe owner-controlled releases | Aspirational; requires certified hardware and model |

## Architectural invariants

- Syntra = lightweight human interface; Aetheris = orchestrator/policy/memory/sandbox; workstation adapter = actual OS authority, **never** the renderer.
- The offline preflight `voice/voice-intent-preflight.cjs` is a **transcript parser, not voice recognition**. It never requests microphone access, produces audio, touches applications, opens network sockets or issues Tauri commands.
- Background execution must never be granted because voice mode is enabled or because Syntra is in CEO/secretary mode.
- High-risk actions use the existing approval engine even when the person asks verbally; audio output, text output and task success state must reflect actual evidence.
- Publicly sourced data and untrusted transcripts are not instructions capable of overriding safety policies.
- Zero mandatory cloud fees are a **deployment requirement**, but useful quality depends on compatible local hardware and model capability. “More intelligent than humans or every AI” is not a verifiable blanket engineering claim; evaluate against real task-specific benchmarks, correctness, security, reliability, latency and owner satisfaction.

## Actual local speech-output development slice

The desktop now includes a **Local speech output** panel on the Overview view. It is disabled by default. An owner must check the opt-in box, enter 1–300 printable characters, and press **Speak**; a **Stop / Mute** button cancels the current utterance. Hiding the app window cancels speech. It uses the WebView `speechSynthesis` API and only uses voices that advertise `localService === true`. No network request, microphone capture, listening, secret storage, prompt persistence, synthetic task progress or model-generated output is added.

`node --test tests/test_syntra_local_voice_output.cjs` covers opt-in, stop and stale callbacks, remote-voice rejection, unsupported webview, input size, error redaction and background-muting. These use a mocked speech engine and are **not a physical Windows audio quality test**. System voice installation/language availability varies by device; unsupported or nonlocal voices fail closed. The speech output cannot hear or recognize “Hi Sintra” until a separately reviewed local wake engine and STT adapter are built and certified.

## Required next build slice

After the owner PC is available, select and audit local wake/STT/TTS adapters for Windows, record their licenses, compute/memory footprint and language benchmarks, then implement native capture/mute with a visible indicator and a gated audio pipeline. Stage host application automation behind exact app IDs, test sandbox policy against malicious speech and untrusted page content, and integrate task speech progress with existing orchestrator events.
