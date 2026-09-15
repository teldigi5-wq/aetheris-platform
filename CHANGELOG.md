# Changelog

All notable repository-level changes to Aetheris are documented here. This changelog describes source and engineering-state changes; it does not convert repository evidence into physical-PC validation.

## Unreleased

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

- Repository documentation now reflects the completed **Stage 34 / 34** roadmap rather than early-stage architecture.
- Contribution guidance now follows the canonical development branch `feature/syntra-aetheris-foundation-v2` and protected `main` promotion flow.
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

The repository roadmap reached **Stage 34 / 34** before the first formal GitHub release. The roadmap completion represents repository-side architecture, governance, validation, and evidence work. It does not assert validation of the eventual owner PC, local-model performance, GPU acceleration, thermals, voice hardware, or unrestricted host execution.

## Release policy

Future release entries should identify:

- the exact tag and commit;
- whether the release is repository-only, pre-PC, or physically validated;
- the validation gates that passed;
- known limitations and deferred physical checks;
- any compatibility, security, governance, or migration impact.

A release must never imply physical validation unless physical evidence exists.
