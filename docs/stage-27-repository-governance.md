# Stage 27 — Canonical Branch Governance & Release Promotion Gate

Date: 2026-09-13

## Objective

Stage 27 turns the branch-consolidation decision into a deterministic repository rule instead of leaving it as a convention.

The repository model is intentionally simple:

- `main` is the stable/release history.
- `feature/syntra-aetheris-foundation-v2` is the single canonical active development line.
- temporary Stage 24/25/26 and integration branches are legacy references and must not receive new development.

Stage 27 is PC-independent. It does not start services, invoke models, execute financial actions, or claim validation on the owner's future physical PC.

## What Stage 27 fixes

Before Stage 27, the repository still displayed multiple temporary development branches even after Stage 24/25 had been consolidated. A later Stage 26 evidence-integrity branch also diverged from the canonical branch.

The Stage 26 evidence-integrity files were reconciled onto the canonical development line. Stage 27 then adds a permanent CI rule that rejects workflow trigger references to those legacy branches and rejects release promotion to `main` from any branch other than the canonical development branch.

## Policy

The policy is stored in:

`configs/stage27/repository-governance.json`

It records:

- stable branch: `main`;
- canonical development branch: `feature/syntra-aetheris-foundation-v2`;
- release-to-main review requirement;
- prohibition on direct stage-branch development;
- the retired branch names;
- the only allowed pull-request source for `main`;
- the pre-PC truth boundary.

## Offline verifier

`scripts/stage27/repository_governance.py` is standard-library-only and deterministic.

It verifies:

1. the stable and canonical branch names have not changed unexpectedly;
2. the canonical branch is not accidentally marked legacy;
3. workflow files do not still trigger from retired Stage 24/25/26 or integration branches;
4. the README exposes the canonical development-line policy and physical-PC truth boundary;
5. a push validation run is occurring on the canonical development branch;
6. a pull request to `main` originates from the canonical development branch;
7. a legacy branch cannot be used for active release promotion.

The verifier does not call the GitHub API and does not pretend that CI policy is the same thing as GitHub branch protection.

## CI gate

`.github/workflows/stage27-repository-governance.yml` runs on:

- pushes to `feature/syntra-aetheris-foundation-v2`;
- pull requests targeting `main`.

The workflow compiles the verifier, runs fail-closed tests, evaluates the current GitHub Actions event context, reasserts the physical-PC truth boundary, and uploads a deterministic governance report.

## Promotion rule

For a pull request targeting `main`:

```text
head must equal feature/syntra-aetheris-foundation-v2
base must equal main
```

Any retired stage/integration branch is rejected by the Stage 27 CI gate.

This is a repository/CI safeguard. GitHub repository-level branch protection or rulesets are a separate administrative control and should be enabled when available; Stage 27 does not falsely claim that administrative protection exists.

## Physical-PC truth boundary

Stage 27 keeps:

`physical_pc_status = BLOCKED_PENDING_HARDWARE`

It makes no claim that WSL2, Docker Desktop, GPU acceleration, local models, sustained thermals, or the full local stack have passed physical-machine testing.

## Result

After Stage 27, new PC-independent work continues on one branch only:

`feature/syntra-aetheris-foundation-v2`

`main` remains the stable promotion target.
