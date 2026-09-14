# Changelog

All notable repository-level changes to Aetheris are documented here. This changelog describes source and engineering-state changes; it does not convert repository evidence into physical-PC validation.

## Unreleased

### Added

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
- Hosted CI is repository evidence, not physical-PC validation.
- `ALLOW` means eligible to execute, not proof that execution occurred.
- Success requires verification evidence or an explicit `UNVERIFIED` result.
- Emergency precedence remains `STOP > TAKE_CONTROL > PAUSE > NORMAL`.
- Live-money execution, withdrawals, and transfers remain outside the default trusted AI path.

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