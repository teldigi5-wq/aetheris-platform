# Aetheris Support Guide

This repository is primarily an engineering portfolio and development platform. Support is community-style rather than a guaranteed service-level offering.

## Before opening an issue

Please check:

- [README.md](README.md) for the current architecture, quick start, and truth boundaries;
- [docs/demo-guide.md](docs/demo-guide.md) for the shortest reproducible demo path;
- [docs/architecture.md](docs/architecture.md) for service and governance boundaries;
- [docs/verification-checklist.md](docs/verification-checklist.md) for evidence expectations;
- [docs/first-boot-runbook.md](docs/first-boot-runbook.md) for the future physical-PC validation phase.

## Bug reports

Use the repository bug-report form when you have a reproducible defect. Include:

- the affected component;
- expected and observed behavior;
- minimal reproduction steps;
- environment details;
- logs or screenshots with secrets removed;
- whether the behavior is repository-side, CI-side, or physical-machine-specific.

Please do not report physical-PC capability as broken when that capability is still explicitly marked `BLOCKED_PENDING_HARDWARE`.

## Feature requests

Use the feature-request form for proposed improvements. Strong proposals explain:

- the problem being solved;
- why it belongs in Aetheris;
- expected safety/governance implications;
- whether it changes cost, privacy, privilege, financial authority, or execution boundaries;
- how success could be verified.

## Security issues

Do **not** include exploit details, credentials, private keys, tokens, personal data, or sensitive logs in a public issue. Follow [SECURITY.md](SECURITY.md).

## What support does not mean

Aetheris does not promise production hosting, financial returns, unrestricted privileged execution, or physical-PC compatibility before evidence exists. Hosted CI remains repository evidence rather than proof of the owner's target hardware.

## Contributing fixes

If you want to submit a fix, read [CONTRIBUTING.md](CONTRIBUTING.md) and use the canonical development flow through `feature/syntra-aetheris-foundation-v2` before stable promotion to protected `main`.