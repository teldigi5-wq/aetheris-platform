# Release Readiness

This document defines the minimum repository-side evidence required before publishing a GitHub release for Aetheris.

A GitHub release is a source/distribution milestone. It must not silently become a claim that the entire platform has been validated on the owner's target physical PC.

## Current state

- Repository roadmap: **Stage 34 / 34 complete**
- Stable branch: protected `main`
- Canonical development branch: `feature/syntra-aetheris-foundation-v2`
- Physical-machine status: `BLOCKED_PENDING_HARDWARE`
- Physical-PC validation remains pending

## Release classes

### Repository pre-PC release

A repository pre-PC release may be published when source, documentation, deterministic validation, and protected-main promotion are complete. Its notes must explicitly state that physical-machine validation remains pending.

### Physical-PC validated release

This classification may be used only after the first-boot and physical-machine verification plan has produced real evidence on the target hardware. Hosted CI cannot satisfy this class by itself.

## Required checks before a repository pre-PC release

- [ ] Candidate commit is on protected `main`.
- [ ] Build workflow passes.
- [ ] CodeQL passes for Java and JavaScript/TypeScript.
- [ ] Foundation dependency-lockdown checks pass.
- [ ] Two-pass reproducible-build comparison passes.
- [ ] Stage 25 First-Boot Readiness passes.
- [ ] Stage 26 Safety & Evidence Certification passes.
- [ ] Stage 27 Repository Governance passes.
- [ ] Stage 28 PC Care Certification passes.
- [ ] Stage 34 master-build specification truth boundary remains intact.
- [ ] `README.md` still reports `Stage 34 / 34` and `BLOCKED_PENDING_HARDWARE`.
- [ ] Release notes identify known limitations and do not imply physical validation.
- [ ] No secrets, credentials, tokens, private keys, or sensitive evidence are included.
- [ ] Changelog is updated for the candidate.

## Optional checks when affected

Run the corresponding regression suites whenever a release changes these surfaces:

- Stage 29 — trading intelligence and execution safety;
- Stage 30 — reasoning and verification;
- Stage 31 — digital twins and bounded self-healing;
- Stage 32 — automation, observability, and emergency control;
- Stage 33 — governance and scoped approvals.

## Release-note template

```text
Aetheris <version> — Repository Pre-PC Release

Commit: <full SHA>
Release class: REPOSITORY_PRE_PC
Physical-machine status: BLOCKED_PENDING_HARDWARE

Highlights
- ...

Validation
- Build: PASS
- CodeQL: PASS
- Dependency lockdown: PASS
- Reproducibility: PASS
- Stage 25–28 stable gates: PASS

Truth boundary
Hosted CI is repository evidence, not physical-PC validation.
Physical-PC validation remains pending.

Known limitations
- ...
```

## Licensing gate

Aetheris currently has no repository-recognized software license. A license must be selected deliberately by the owner before presenting the project as generally licensed for third-party reuse. This checklist does not choose a license automatically because licensing changes legal rights and obligations.

## Physical validation gate

A release must not use labels such as `PHYSICAL_PC_VALIDATED`, `production-ready on target PC`, or equivalent language until the physical validation runbook has been executed on real hardware and supporting evidence has been reviewed.

The authoritative product/safety contract remains [master-build-spec.md](master-build-spec.md).