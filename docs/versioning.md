# Versioning and Release Classes

Aetheris uses semantic versioning for repository milestones, with explicit release-class language so version numbers never overstate physical validation.

## Version format

The project follows `MAJOR.MINOR.PATCH` semantics for published GitHub releases.

- `MAJOR` — incompatible public/runtime contract changes or the first fully declared 1.x product line.
- `MINOR` — backward-compatible capability additions.
- `PATCH` — backward-compatible fixes, documentation corrections, hardening, or maintenance.

Pre-release identifiers are used before a release reaches the next trust boundary.

## Pre-PC release line

Before physical validation on the owner's target machine, formal repository milestones use the `pre-pc` identifier.

Recommended first release candidate:

`v0.1.0-pre-pc`

This means:

- the repository roadmap has reached **Stage 34 / 34**;
- protected-main repository checks have passed for the release candidate;
- the release is suitable for source review, architecture review, interview demonstration, and repository-side reproduction;
- physical-machine status remains `BLOCKED_PENDING_HARDWARE`;
- Physical-PC validation remains pending.

The `pre-pc` identifier must not be removed merely because hosted CI passes.

## Physical-PC validated line

A release may drop the `pre-pc` qualification only after the physical first-boot and hardware validation plan has produced reviewed evidence on the actual target machine.

That evidence must cover the relevant claims being made, such as:

- WSL2 / virtualization state;
- Docker and local service startup;
- local-model availability and GPU acceleration where claimed;
- workstation packaging and permissions;
- thermals, storage and resource behavior where claimed;
- voice, browser, phone or device integrations where claimed;
- emergency controls and owner-policy behavior on the real machine.

Hosted CI is repository evidence, not physical-PC validation.

## v1.0.0 policy

`v1.0.0` is intentionally reserved for a later milestone. Publishing it should require all of the following:

1. an explicit software-license decision by the repository owner;
2. stable public documentation and migration/compatibility notes;
3. protected-main release gates passing on the exact release commit;
4. physical-machine validation for every hardware/runtime claim included in the release notes;
5. no unresolved critical security or governance blockers for the declared scope.

Aetheris can publish useful `0.x` repository releases before `v1.0.0`; the version number is not a substitute for evidence.

## Tagging rules

- Tags must point to a commit already present on protected `main`.
- Release notes must name the exact commit SHA.
- A tag must not be moved after publication except to correct an accidental unpublished/local tag.
- Published release tags are immutable project history.
- Pre-release tags must remain clearly marked as pre-release in GitHub.

## Truth boundary

A release version communicates a software milestone. It does not by itself prove execution, safety, physical readiness, or deployment success.

`ALLOW` means eligible to execute, not proof that execution occurred. Success requires verification evidence or an explicit `UNVERIFIED` state. Emergency precedence remains `STOP > TAKE_CONTROL > PAUSE > NORMAL`.

The authoritative build and safety contract remains [master-build-spec.md](master-build-spec.md), and the release gate is defined in [release-readiness.md](release-readiness.md).
