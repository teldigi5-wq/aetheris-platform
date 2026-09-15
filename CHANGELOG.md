# Changelog

All notable repository-level changes to Aetheris are documented here. This changelog describes source and engineering-state changes; it does not convert repository evidence into physical-PC validation.

## Unreleased

### Evidence-first portfolio proof

- Added a machine-readable core-platform evidence manifest at `build-evidence/portfolio/core-platform-evidence.json`.
- Added `tools/validate_portfolio_evidence.py`, which fails closed when a claimed core-platform evidence path or required marker disappears.
- Added regression tests for missing evidence, missing markers and truth-boundary drift.
- Added the dedicated `Portfolio Evidence` CI workflow, including two-pass deterministic report comparison and an uploaded evidence artifact.
- Added `docs/portfolio-evidence.md` with claim-to-source mappings, reviewer commands and explicit limits on what repository evidence proves.
- Added `docs/portfolio-case-study.md` as a recruiter/interview case study centered on defensible platform-engineering decisions and trade-offs.
- Linked the evidence pack and case study from `docs/portfolio-scope.md` so the current README reviewer path leads to verifiable proof rather than feature-count claims.
- Runtime proof remains separate: no screenshots, external adoption, production-readiness or physical-machine success is claimed by this repository validator.

### Portfolio credibility consolidation

- Reframed the root README so the primary portfolio story is the cloud-native platform foundation: gateway, identity, PostgreSQL, Redis, RabbitMQ, observability, resilience, Docker, Kubernetes and Helm.
- Added `docs/portfolio-scope.md` with explicit maturity labels separating core platform engineering from later Syntra/Aetheris research and pre-PC integrations.
- Reworked the interview and demo guides to lead with defensible distributed-systems/platform engineering and discuss AI/operator extensions only as a secondary track.
- Retained the later **Stage 34 / 34** roadmap statement for historical/validation-contract continuity while explicitly preventing it from becoming the headline portfolio claim.
- Preserved published development history rather than rewriting it for appearance; stale temporary branches remain scheduled for final project cleanup.

### Release preparation

- Prepared the first formal repository pre-PC release candidate: `v0.1.0-pre-pc`.
- Added semantic versioning and release-class rules that reserve `v1.0.0` for a later, explicitly validated milestone.
- Added GitHub generated-release-note categorization through `.github/release.yml`.
- Added dedicated release-candidate notes in `docs/releases/v0.1.0-pre-pc.md`.
- No GitHub release or immutable tag has been published yet.

### Added

- Aetheris Operator v2 site-skill catalog and deterministic autonomous-web-task compiler with per-skill physical-validation gates.
- Starter LinkedIn profile-inspection/About-update and Vercel deployment-inspection/trigger templates, each constrained to its declared domain and evidence contract.
- Bounded read-only recovery with a maximum of two attempts, while external mutation skills receive no blind automatic retry.
- Ephemeral required-value references for mutation inputs, per-skill validation allowlisting, and regression tests that keep unvalidated production-site selectors fail-closed.
- Aetheris Operator v1 generic browser control plane with domain allowlists, deterministic action/effect classification, owner-policy evaluation, approval gates, emergency-stop enforcement and evidence requirements.
- A loopback-only W3C WebDriver adapter for future Chrome/Edge browser execution on the owner PC, with redirect containment after every action and no remote WebDriver control path.
- Generic browser actions for navigation, click, typed input through ephemeral value references, upload through file references, screenshots, bounded extraction and waits; downloads fail closed until file evidence exists.
- Browser regression tests covering runtime truth, domain containment, non-HTTP rejection, `PRIVATE` protected-data blocking, approval-gated mutations, financial hard-blocks and disabled-runtime execution.
- Aetheris Reach Layer v1 with a 15-channel external-information catalog, preferred/fallback backend modeling, governed routing, runtime doctor reporting, non-mutating install planning, and explicit implementation-state truth.
- Reach policy integration so runtime routes remain subject to owner rules, `ZERO_COST`, `PRIVATE`, credential readiness, and physical-validation boundaries.
- Reach regression tests that prevent planned social/web adapters from being reported as working capabilities before their execution adapters exist.
- Public Excellence documentation and reviewer orientation.
- Current Syntra × Aetheris architecture and interview guide.
- Reproducible demo guide.
- Community support and conduct documentation.
- Release-readiness checklist for a future repository release.

### Changed

- Repository documentation now reflects the completed **Stage 34 / 34** roadmap without making stage count the primary recruiter narrative.
- Contribution guidance follows the canonical development branch `feature/syntra-aetheris-foundation-v2` and protected `main` promotion flow.
- Dependabot routine version-update PRs are disabled while security/vulnerability updates remain available.

### Safety and truth boundaries

- Physical-machine status remains `BLOCKED_PENDING_HARDWARE`.
- Physical-PC validation remains pending.
- Operator v2 starter site skills are repository templates only until each exact workflow is physically validated; validating one skill never validates another.
- Generic browser repository support does not assert that Chrome/Edge, WebDriver, authenticated sessions or site-specific workflows are validated on the owner PC.
- Hosted CI is repository evidence, not physical-PC validation.
- `ALLOW` means eligible to execute, not proof that execution occurred.
- Success requires verification evidence or an explicit `UNVERIFIED` result.
- Emergency precedence remains `STOP > TAKE_CONTROL > PAUSE > NORMAL`.
- Live-money execution, withdrawals, and transfers remain outside the default trusted AI path, including the generic browser path.

## Repository roadmap completion

The later Syntra × Aetheris repository roadmap reached **Stage 34 / 34** before the first formal GitHub release. This represents repository-side architecture, governance, validation and evidence work. It does not assert validation of the eventual owner PC, local-model performance, GPU acceleration, thermals, voice hardware or unrestricted host execution.

## Release policy

Future release entries should identify:

- the exact tag and commit;
- whether the release is repository-only, pre-PC, or physically validated;
- the validation gates that passed;
- known limitations and deferred physical checks;
- any compatibility, security, governance, or migration impact.

A release must never imply physical validation unless physical evidence exists.
