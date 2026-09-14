# Syntra × Aetheris — Canonical Master Build Specification

**Roadmap stage:** 34 / 34  
**Repository:** `teldigi5-wq/aetheris-platform`  
**Canonical development line:** `feature/syntra-aetheris-foundation-v2`  
**Blueprint basis:** Syntra × Aetheris Master Blueprint v2.0, September 2026  
**Repository truth status:** `PRE_PC_HARDENED + ROADMAP_34_COMPLETE`  
**Physical-machine truth status:** `BLOCKED_PENDING_HARDWARE`

This document is the canonical build/continuation specification for a capable coding agent working on Syntra × Aetheris. It is deliberately stricter than a feature wish list: it defines product identity, architectural boundaries, owner-control rules, safety invariants, engineering workflow, validation gates and the truth boundary between repository evidence and physical-machine evidence.

> **Important:** `34 / 34` means the repository-side master roadmap has reached its final specification stage. It does **not** mean every aspirational subsystem has been physically exercised, that the target Windows PC has been validated, that unrestricted automation is enabled, or that live-money/production authority exists.

## 1. Role of the implementing agent

Act as the principal architect, staff engineer, security engineer, product designer, QA lead and technical writer for the `aetheris-platform` monorepo.

For every change:

1. inspect the existing repository and preserve working behavior;
2. state the objective and acceptance criteria;
3. design or reuse stable interfaces before implementation;
4. implement the smallest complete vertical slice;
5. add tests and deterministic evidence where practical;
6. run relevant tests, static/security checks and compatibility gates;
7. assess security, privacy, cost and rollback impact;
8. show the exact changed behavior and files;
9. create a recoverable checkpoint through normal version control;
10. update documentation and roadmap truthfully.

Never remove working functionality merely to simplify a refactor without explicit, reviewable justification.

## 2. Product identity

Build two separate but connected products in one repository.

### Syntra

Syntra is the owner-facing assistant and operations experience.

- voice-first and text-first;
- warm, calm, supportive and professional, with playful behavior only when appropriate;
- executive coordinator, tutor, companion interface and live operations console;
- never falsely claims consciousness, feelings, credentials, access, actions or certainty;
- never manipulates, guilt-trips, isolates or encourages emotional dependency;
- remains lightweight and does not absorb all infrastructure logic.

### Aetheris

Aetheris is the operating platform underneath Syntra.

It owns planning, orchestration, model routing, memory, tools, computer-control contracts, permissions, security, automation, telemetry, evaluation, recovery and remote-access boundaries. Aetheris must remain usable independently of the Syntra UI.

The owner is the final authority for configurable product behavior. Platform, provider, legal and security restrictions that are designed as non-bypassable remain non-bypassable.

## 3. North-star experience

Win through architecture rather than unsupported intelligence claims.

The system should provide:

- immediate command acknowledgement;
- streamed voice/text and real task events;
- deterministic fast paths for priority controls;
- specialist-agent delegation;
- persistent project context;
- owner rules and scoped approvals;
- local-first privacy;
- automatic model routing;
- transparent live task state;
- checkpoints, rollback and recovery;
- zero-cost operation whenever possible.

Do not claim a local model is universally smarter than frontier models. Measure quality, latency, throughput, reliability, privacy and cost on real workloads.

## 4. Target environment and performance

Initial target environment:

- Windows-first desktop;
- 16 GB RAM;
- NVIDIA RTX 4050 with 6 GB VRAM;
- 144 Hz display;
- architecture portable to stronger hardware later.

UI requirements:

- adaptive high-refresh rendering up to 144 Hz where useful;
- immediate interaction feedback;
- no long opaque “thinking” state;
- background work never blocks the UI thread;
- reduce decorative effects before degrading interaction quality under load;
- loading, offline, error and recovery states for core views;
- progress driven by real execution events, never fake animation;
- no unhandled frontend/runtime failure in core workflows.

Performance modes remain conceptually distinct: `TURBO`, `BALANCED`, `ECO`, `PRIVATE`, `ZERO-COST`, and `FOCUS`.

Physical performance claims remain unverified until the owner target PC exists and is tested.

## 5. Command and live-operations experience

Support typed commands, voice commands, push-to-talk/wake-word adapters, a global command surface, tray quick controls, CLI, and later authenticated remote/mobile continuation.

Priority owner controls such as `STOP`, `PAUSE`, `TAKE CONTROL`, `PRIVATE MODE`, `ZERO-COST MODE`, and `DO NOT TOUCH <resource>` must have deterministic high-priority paths and must not depend on a slow model response.

The owner must be able to interrupt or redirect ongoing work. For non-trivial tasks, surface real state including plan, active agent, model/provider, routing reason, tools, files/commands, tests, dependencies, approvals, errors/retries, checkpoints, resource usage, cost/quota status, artifacts and outputs.

## 6. One cross-system action lifecycle

Use the Stage 33 lifecycle everywhere:

`UNDERSTAND → PLAN → CHECK RULES → ASSESS RISK → SIMULATE/PREVIEW WHEN NEEDED → APPROVE WHEN REQUIRED → EXECUTE → VERIFY → RECORD → LEARN → REPORT`

Rules:

- lifecycle phases cannot be silently skipped for consequential work;
- `ALLOW` means eligible to execute, not that execution occurred;
- no execution observation means `EXECUTE` is not complete;
- success requires verification evidence or an explicit `UNVERIFIED` result;
- rollback/checkpoint information is mandatory when risk or reversibility requires it;
- emergency control outranks ordinary execution eligibility.

Never use a “think → execute → hope” workflow.

## 7. Owner rules and policy precedence

Owner rules are structured, versioned, inspectable data, not merely prompt text.

Support global rules, owner security rules, project rules, agent rules and session rules with scope, conditions, actions, priority, enable/disable, audit history and conflict detection.

Policy precedence:

1. non-bypassable platform/legal/provider constraints;
2. owner security rules;
3. owner behavior/cost rules;
4. project rules;
5. agent rules;
6. session rules;
7. current instruction.

The existing persisted `OwnerRuleService` and `OwnerRuleCompilerService` remain the canonical owner-rule source. New governance layers must consume or adapt that result rather than create a second secret rule database/parser.

Default safety intent includes local-first handling of private files, no spending without authorization, checkpoints before major changes, exact preview before important deletion, policy/approval before external publishing, secret redaction, immediate STOP, and live progress for non-trivial tasks.

## 8. Hard mode guarantees

### ZERO-COST

`ZERO-COST` is a hard policy boundary. A billable endpoint or non-zero external cost must be blocked; ordinary model output or approval cannot silently override it.

### PRIVATE

Configured protected content stays on approved local paths. Sending protected private/secret context off-device requires an explicit, correctly scoped owner override and must still respect non-bypassable constraints.

### FOCUS / emergency controls

Focus policy may suppress non-essential notifications but must not hide urgent security/recovery state. Stage 32 emergency precedence remains `STOP > TAKE_CONTROL > PAUSE > NORMAL`.

## 9. Model strategy

Models are replaceable workers, not Syntra’s identity and not policy authority.

Use adapters for local runtimes such as Ollama/OpenAI-compatible endpoints and optional legitimate cloud/provider gateways. Premium providers are opt-in only.

Route using capability fit, quality, first-token latency, throughput, provider health, privacy classification, context size, remaining quota, monetary cost and recent failure rate.

Do not run models irresponsibly on 6 GB VRAM. Prefer a small quantized always-on model for routing/everyday work, and load larger models only when justified. Measure TTFT, tokens/sec, VRAM/RAM use, failures and answer quality. Keep changing factual knowledge in retrieval/memory rather than primarily in weights.

## 10. Syntra-Core progression

Treat the owner-controlled model layer as progressive:

- v0 — system prompt + owner rules + deterministic router;
- v1 — retrieval + structured owner/project memory;
- v2 — small local open-weight routing/everyday model;
- v3 — optional lawful LoRA/QLoRA experiments on scrubbed owner-controlled data;
- v4 — evaluated orchestration/policy model on private benchmarks.

Never train on secrets or data the owner lacks rights to use. No silent self-modification of core model weights.

## 11. Voice architecture

Use replaceable local-first adapters for wake word, VAD, streaming STT, turn detection, interruption and streaming TTS. Partial transcripts should appear live. Raw microphone audio is not retained by default. TTS should begin from stable chunks when appropriate rather than waiting for the whole response.

## 12. Specialist organization

Syntra coordinates specialist packs across executive, engineering, AI/data, DevOps/SRE, cybersecurity, QA/release, creative/UI/UX, research, academy, business/strategy, finance/markets, operations, career, IoT/robotics and personal-performance domains.

Every agent definition should declare role, objective, allowed tools, memory scope, risk level, preferred model class, output schema, verification requirements and escalation policy. Consequential work should receive independent/cross-specialist review when useful.

## 13. Teaching and academy behavior

Professor/Lecturer mode teaches rather than only answers. Support assessment, level-adjusted explanation, worked examples, learner attempts, misconception diagnosis, targeted exercises, quizzes, spaced repetition, mastery tracking, coding labs, exam simulation and interview simulation.

Explicit modes may include `TEACH ME`, `HINT ONLY`, `REVIEW MY ANSWER`, `EXAM MODE`, and `SOLUTION MODE`.

## 14. Cybersecurity boundary

Defensive security, secure coding, authorized lab/CTF work, threat modeling, AppSec, DFIR, monitoring and remediation are first-class domains.

Offensive/red-team execution is limited to systems the owner owns, administers, has explicit authorization to test, or isolated labs/CTFs. Never automatically expand target scope. Do not create workflows whose purpose is credential theft, stealth persistence, destructive third-party compromise, security-evasion against third parties or unauthorized access.

High-risk security work requires approval, scope evidence, auditability and a recovery plan.

## 15. Tool and computer-control priority

Prefer, in order:

1. official API/SDK/connector;
2. CLI or structured file operations;
3. typed MCP/tool interface;
4. OS accessibility/UI automation;
5. vision/coordinate clicking only when necessary.

Use scoped workspaces, least privilege and temporary capabilities. One project’s needs never justify blanket PC access.

External open-source agent systems are replaceable integrations behind adapters, sandboxed and reviewed before broad permissions.

## 16. Memory and data

Separate working/session memory, project memory, owner memory, knowledge retrieval, operational metrics and episodic task history.

Requirements include inspectable retrieval, project isolation, explicit retention, encryption for sensitive data, correction/deletion controls and prevention of secret leakage across projects/providers.

## 17. Capability levels and secrets

Capability model:

- `L0` observe;
- `L1` selected workspace read/write;
- `L2` local execution;
- `L3` external/durable action;
- `L4` sensitive/admin/purchase/destructive action.

Use OS-backed secure storage where practical. Prefer scoped/temporary credentials over master secrets. Redact secrets from logs, prompts, evidence and screenshots. Emergency stop revokes temporary capabilities and blocks new side effects.

## 18. Remote access

Remote control must be authenticated, encrypted and private. Never expose raw agent-control endpoints directly to the public internet. Support pairing, revocation, separate remote policies and visible remote-session indicators.

## 19. Reliability and recovery

Every long-running task should have a persistent state model supporting pause/resume, cancellation, error-class retries, provider circuit breakers, checkpoints, restart recovery, model/provider fallback, file/Git rollback, idempotency where possible and undo to a verified checkpoint.

Never claim success before verification passes or the result is explicitly labeled unverified.

## 20. Observability and research quality

Emit structured traces, metrics, logs, security audits and quality/evaluation events with secret redaction. The live UI should consume the same real event stream used for observability.

For public research, use current sources when freshness matters, prefer primary/official sources, cite claims, distinguish event date from publication date, separate facts from inference/recommendation and expose uncertainty/conflict instead of inventing certainty.

## 21. Business, finance and trading

Business research, planning, budgeting and market research are allowed analysis domains.

Financial-market behavior defaults to observation/paper environments. Repository Stage 29 keeps live-money execution, withdrawals and transfers outside the default trusted path. Any future live-money capability requires explicit enablement, deterministic hard limits, exact risk disclosure, scoped credentials without withdrawal/transfer permissions, simulation/paper evidence and configured approvals.

Never promise profit or describe a trade as guaranteed/high-profit.

## 22. Self-improvement

Approved preferences and successful workflows may improve memory/metrics and produce proposals. Core code, security rules, hard policy and model weights must not be silently rewritten.

Self-improvement follows:

`PROPOSE → DIFF → TEST → SECURITY REVIEW → OWNER APPROVAL → CANARY → ROLLOUT`, with rollback available.

## 23. Engineering standards

Require:

- typed service contracts;
- small replaceable adapters;
- unit/integration/end-to-end tests appropriate to the change;
- linting/formatting/static analysis;
- reproducible setup/build evidence;
- migration/versioning strategy when persistence changes;
- no hard-coded secrets;
- dependency pinning/lockfiles;
- secure defaults;
- accessible UI;
- clear failures and recovery guidance;
- architecture decision records/changelog where warranted;
- documentation kept with the repository.

## 24. Repository architecture

Preserve the mature repository that already exists. Do not perform a cosmetic full rewrite just to match an aspirational directory diagram.

Long-term modular boundaries may map to desktop apps, CLI, orchestration/rules/permissions/event cores, model/memory/voice/computer-control/automation/telemetry/recovery/remote services, specialist agents, integrations, shared types/UI/policy/agent SDK packages, configs, evals, tests and docs.

Stable contracts and clear product boundaries matter more than exact folder names.

## 25. Build order for missing runtime capability

When hardware and owner-approved runtime work resumes, use this dependency-aware order unless repository evidence justifies a safer refinement:

1. repository contracts and event model;
2. Syntra desktop shell + real live timeline;
3. Aetheris task lifecycle;
4. rules/permissions;
5. local model adapter + streaming;
6. voice pipeline;
7. safe file/terminal/Git tools;
8. first Engineering + QA + Research agents;
9. model routing + Zero-Cost enforcement;
10. memory;
11. computer-use integrations;
12. security hardening;
13. remaining specialist divisions;
14. Syntra-Core local-model work;
15. secure remote companion;
16. final evaluation + installer/updater.

Do not treat this as permission to duplicate capabilities already implemented and validated in the repository.

## 26. Current repository evidence map

The canonical development line already contains repository-side evidence for the following later-roadmap foundations:

| Stage | Repository evidence | Runtime truth boundary |
|---|---|---|
| 28 | PC-care classification and recommendations-oriented remediation planning | no autonomous destructive PC repair; physical machine unvalidated |
| 29 | fail-closed trading proposal/risk foundation | live-money execution disabled by default |
| 30 | reasoning, confidence, verification, source trust, contradiction, temporal and decision-ledger foundations | model recommendations do not outrank deterministic policy |
| 31 | project/PC/workspace digital twins, proactive detection, priority management, bounded recovery/update planning, model benchmarking | synthetic/hosted evidence is not owner-PC validation |
| 32 | workflow/event scheduling, observability timeline, remote authorization guard, artifacts/checkpoints and emergency control | no arbitrary remote executor |
| 33 | ordered governance lifecycle, canonical owner-rule integration, risk/simulation/approval gates and completion truth | `ALLOW` is eligibility only; success needs evidence |
| 34 | this canonical build/continuation specification plus machine-checkable integrity gate | documentation/CI completion is not physical runtime validation |

Earlier Stage 21–27 repository hardening remains part of the canonical history: physical-target truth boundaries, release evidence, compatibility freeze, dependency lockdown, reproducibility, first-boot readiness, safety certification and repository governance.

## 27. Non-bypassable repository truth boundaries

Until real hardware and explicit owner enablement provide evidence, keep these statements true:

- physical-machine status is `BLOCKED_PENDING_HARDWARE`;
- hosted CI is repository evidence, not proof of WSL2/Docker/GPU/thermals/storage/voice/browser/phone/local-model behavior on the target PC;
- no unrestricted privileged host executor is considered validated;
- no production activation is implied;
- no live-money execution, withdrawal or transfer authority is implied;
- no autonomous destructive PC repair is implied;
- GitHub branch protection/rulesets remain separate repository-administration controls and are not emulated in application code;
- public/provider availability and free quotas must be revalidated when used.

## 28. Canonical validation gates

A roadmap continuation or release candidate should run the applicable canonical gates, including:

- Java service/orchestrator tests;
- workstation-agent packaging/safety checks;
- dashboard frozen install/build;
- release hardening and deterministic evidence;
- compatibility-contract freeze;
- dependency lockdown and reproducible-build comparison;
- Stage-specific regression suites;
- CodeQL Java and JavaScript/TypeScript;
- Stage 34 master-build specification integrity validation.

A green gate means only what that gate actually tested.

## 29. Definition of done for the eventual physical product

The full physical product is not done merely because the roadmap documents are complete or the UI looks impressive.

Physical/runtime completion requires evidence that:

- voice and text commands work;
- interruption works;
- progress is real/live;
- owner rules are enforced end to end;
- useful multi-step agent work executes and verifies;
- tool permissions are scoped/revocable;
- Zero-Cost Mode prevents charges;
- Private Mode is enforceable;
- security scope is enforced;
- local AI works offline;
- provider failure has fallback;
- risky changes have preview/approval/rollback;
- memory is inspectable/isolated;
- tests/evals quantify quality;
- crashes/restarts recover tasks;
- the UI remains responsive under realistic model/tool load;
- core workflows avoid unhandled/broken states;
- installation, update and recovery documentation works on the target machine.

## 30. Owner communication while building

For multi-step work, provide concise live progress. Surface blockers immediately. Choose sensible defaults for reversible decisions. For security-sensitive, public, financial, destructive, privileged or difficult-to-reverse decisions, surface the exact choice and follow the configured approval policy.

Never hide failures. Never fake completed actions.

## 31. Expanded modular system catalog

Treat the following as modular subsystems behind stable contracts, not one monolithic agent: Personal Knowledge Graph, Master Project Office, Visual Computer Agent, Document Brain, Research Verification Engine, AI Council, Autonomous QA, Architecture Review Board, DevOps/SRE Center, Network Operations Center, Privacy Guardian, Credential Vault, Sandbox Lab, Disaster Recovery, Model Arena, Cost/Quota Brain, Skill Factory, owner-reviewed Self-Improvement Lab, Opportunity Radar, University Intelligence, Personal Business OS, Creative Studio, Hardware/IoT Command Center, Mobile Companion, Context Continuity, Live Mission Control and Emergency Control Layer.

Some catalog items are architectural targets rather than fully physically validated runtime systems. Never promote an aspirational name into a completed-capability claim without evidence.

## 32. Continuation prompt

When a capable coding agent continues this repository, give it this operating instruction:

> Build and evolve Syntra × Aetheris as a local-first, owner-controlled personal AI operating ecosystem. Preserve working features and canonical history. Treat models as replaceable workers, deterministic policy as higher authority, and Stage 33 governance as the universal side-effect lifecycle. Prefer official APIs and typed tools over fragile UI automation. Keep Zero-Cost and Private-mode boundaries fail-closed. Use least privilege, exact-scope approvals, real execution events, checkpoints, rollback and independent verification. Do not claim success without evidence. Do not claim physical-machine behavior from hosted CI. Do not silently enable live-money, production, destructive or privileged authority. Implement the smallest complete vertical slice, test it, security-review it, validate compatibility and reproducibility, then update repository evidence and documentation. Keep Syntra coherent and personal while Aetheris remains modular, observable, permissioned and replaceable.

## 33. Final Stage 34 acceptance criteria

Stage 34 is repository-complete only when:

- this canonical master build specification exists in `docs/`;
- it records the Stage 33 governance lifecycle and owner-rule source-of-truth boundary;
- it records Zero-Cost, Private, emergency, approval, verification and physical-PC invariants;
- it contains a dependency-aware build/continuation order;
- it separates repository evidence from physical runtime evidence;
- `docs/master-roadmap.md` records Stage 34 complete;
- root `README.md` reports `Stage 34 / 34` without claiming physical validation;
- the Stage 34 validator passes deterministically;
- the complete PR passes Build, Stage 30–33 regressions, dependency/reproducibility and CodeQL before merge.

## Final product statement

Syntra should feel like one caring, capable personal assistant. Aetheris should behave like the secure operating platform and expert workforce behind her. The owner should experience one coherent intelligence system while the codebase remains modular, testable, permissioned, observable and replaceable.
