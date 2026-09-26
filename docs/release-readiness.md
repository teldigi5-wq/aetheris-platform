# Release Readiness

This document defines the minimum repository-side evidence required before publishing a GitHub release for Aetheris after the AI-runtime extraction.

A GitHub release is a source/distribution milestone. It must not silently become a claim that the entire project has been validated on the owner's target physical PC.

## Current state

- Historical repository roadmap: **Stage 34 / 34 complete**
- Platform stable branch: protected `main`
- Stage 27 canonical development-line marker: `feature/syntra-aetheris-foundation-v2`
- AI runtime source owner: `teldigi5-wq/aetheris-ai-runtime`
- Platform-recorded certified runtime checkpoint: `65a6262717adcd52ac8d8a16ed6f223e299fd74d`
- Software license: **Apache License 2.0** for both Aetheris source-owner repositories
- Physical-machine status: `BLOCKED_PENDING_HARDWARE`
- Physical-PC validation remains pending

The certified runtime checkpoint and the current runtime repository `main` are different concepts. A later runtime commit does not become platform-certified merely because it is newer or because documentation-only CI passed.

## Release classes

### Platform repository pre-PC release

A platform pre-PC release may be published when platform source, documentation, deterministic validation, external-runtime boundary evidence and protected-main promotion are complete. Its notes must explicitly state the runtime checkpoint it targets and that physical-machine validation remains pending.

### AI-runtime repository release/checkpoint

Runtime source is released/certified from `teldigi5-wq/aetheris-ai-runtime`. Runtime-owned safety/regression evidence must be evaluated there. Platform release notes may reference an exact certified runtime SHA, but must not relabel an arbitrary runtime revision as certified.

### Physical-PC validated release

This classification may be used only after the first-boot and physical-machine verification plan has produced real evidence on the target hardware. Hosted CI in either repository cannot satisfy this class by itself.

## Required checks before a platform repository pre-PC release

- [ ] Candidate platform commit is on protected `main`.
- [ ] Platform Build workflow passes on the exact candidate commit.
- [ ] Platform CodeQL passes for Java and JavaScript/TypeScript on the exact candidate commit.
- [ ] Platform portfolio/release evidence checks pass when triggered for the candidate.
- [ ] Platform core-source-independence checks prove runtime-owned source roots are absent.
- [ ] Stage 25 platform + external-runtime readiness evidence remains green when applicable.
- [ ] Stage 26 platform evidence certification remains green when applicable.
- [ ] The Stage 27 promotion gate passed on the reviewed path into `main`; do not bypass it with an unapproved branch class.
- [ ] `architecture/ai-runtime-certification-reference.json` names the intended certified runtime checkpoint.
- [ ] The referenced runtime checkpoint has its required runtime-owned certification evidence in `aetheris-ai-runtime`.
- [ ] Platform/runtime boundary assets remain consistent: contract, certification reference and external integration composition.
- [ ] Stage 34 historical truth-boundary language remains intact where validators depend on it.
- [ ] `README.md` still contains `Stage 34 / 34`, `Physical-PC validation remains pending` and `BLOCKED_PENDING_HARDWARE` truth markers required by repository governance.
- [ ] `LICENSE` exists in both Aetheris source-owner repositories and the selected project license remains Apache License 2.0.
- [ ] Release notes identify known limitations and do not imply physical validation, production activation or registry publication.
- [ ] No secrets, credentials, tokens, private keys or sensitive evidence are included.
- [ ] Changelog/release notes identify both the platform commit and the certified runtime checkpoint when the release includes AI-runtime integration.

## Runtime-owned checks when the runtime changes

The following areas are owned by `aetheris-ai-runtime`, not by a duplicate platform source tree:

- complete migrated runtime regression;
- Stage 26 runtime safety certification;
- Stage 28 PC-care certification;
- Stage 29 trading-intelligence certification;
- reasoning/verification, digital-twin/recovery and governance regressions when affected;
- runtime image/provenance evidence when a runtime artifact is being certified.

If the runtime revision changes, recertify the runtime first, then advance the platform certification reference through reviewed integration evidence. Do not change the pinned SHA just to follow runtime `main`.

## Release-note template

```text
Aetheris <version> — Repository Pre-PC Release

Platform commit: <full platform SHA>
Certified AI runtime: <full certified runtime SHA>
Release class: REPOSITORY_PRE_PC
Physical-machine status: BLOCKED_PENDING_HARDWARE

Highlights
- ...

Platform validation
- Build: PASS
- CodeQL: PASS
- Core source independence: PASS
- Platform evidence / external-runtime readiness: PASS

Runtime validation
- Certified runtime checkpoint: <SHA>
- Applicable runtime-owned certification: PASS

Truth boundary
Hosted CI is repository evidence, not physical-PC validation.
Physical-PC validation remains pending.
No production-activation, registry-publication or live-money execution claim is implied.

Known limitations
- ...
```

## Licensing gate — satisfied

The owner selected the **Apache License 2.0** for both `aetheris-platform` and `aetheris-ai-runtime`. Each repository must retain its canonical `LICENSE` file, and third-party dependencies or incorporated materials remain subject to their own licenses. Licensing is a reuse/distribution decision only; it does not change certification, hardware-validation or production truth boundaries.

## Physical validation gate

A release must not use labels such as `PHYSICAL_PC_VALIDATED`, `production-ready on target PC`, or equivalent language until the physical validation runbook has been executed on real hardware and supporting evidence has been reviewed.

The authoritative product/safety contract remains [master-build-spec.md](master-build-spec.md), the split-repository topology is described in [architecture.md](architecture.md), and physical acceptance steps remain in [first-boot-runbook.md](first-boot-runbook.md).
