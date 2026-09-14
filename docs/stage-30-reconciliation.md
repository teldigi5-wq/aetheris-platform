# Stage 30 canonical-line reconciliation

## Why this reconciliation exists

The repository developed along two histories after the original foundation split:

- `main` received Stage 30 reasoning-control work and newer repository documentation/security workflow changes;
- `feature/syntra-aetheris-foundation-v2` retained the much larger governed Aetheris/Syntra implementation, including the orchestrator, workstation agent, Stage 24–29 hardening, reproducible-build controls and stage-specific governance.

Stage 30 must not replace that mature foundation or pretend the smaller `main` history contains it.

## Reconciliation strategy

This change starts from `feature/syntra-aetheris-foundation-v2` and layers the Stage 30 reasoning-control package on top without rewriting the existing 321-commit development history.

Imported Stage 30 surfaces:

- `aetheris-reasoning/`
- `docs/master-roadmap.md`
- `docs/stage-30-reasoning.md`
- a pinned, deterministic Stage 30 test workflow
- a pinned CodeQL workflow covering the mature Java surfaces and dashboard

## Preserved boundaries

- Existing Stage 24–29 workflows and evidence remain authoritative for those stages.
- Existing dependency locks and immutable GitHub Action pins remain intact.
- Stage 30 does not enable external side effects by itself.
- Hosted CI still does not count as physical-PC validation.
- Live-money execution remains outside the default trusted path.
- GitHub-hosted branch protection/rulesets remain a repository-owner administrative setting.

## Promotion rule

After this reconciliation is green, merge it into `feature/syntra-aetheris-foundation-v2`. Stage 31 should then start from that reconciled development line. Promotion of the entire feature line to `main` remains a separate reviewed release decision.
