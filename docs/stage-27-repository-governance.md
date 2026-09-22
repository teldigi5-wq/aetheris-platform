# Stage 27 — Canonical Branch Governance & Release Promotion Gate

Date: 2026-09-13

## Objective

Stage 27 turns the branch-consolidation decision into a deterministic repository rule instead of leaving it as a convention.

The repository model is intentionally simple:

- `main` is the stable/release history.
- `feature/syntra-aetheris-foundation-v2` is the single canonical active development line.
- short-lived `release/*` branches may carry a narrowly scoped, reviewed promotion into `main`.
- temporary stage, integration, proof, and release branches must not become parallel development lines.

Stage 27 is PC-independent. It does not start services, invoke models, execute financial actions, or claim validation on the owner's future physical PC.

## What Stage 27 governs

Stage 27 establishes roles rather than pretending that repository history contains only two Git refs.

The canonical development line remains:

`feature/syntra-aetheris-foundation-v2`

Stable shipped history remains:

`main`

When a stable promotion should intentionally ship only a bounded subset of certified work, a short-lived `release/<name>` branch may be created from an appropriate stable baseline, reviewed through a pull request to `main`, and removed after merge. A release branch is a promotion carrier, not an active development branch.

Historical refs may exist temporarily while repository-hygiene review is in progress. Their presence does not grant them development or release authority.

## Policy

The policy is stored in:

`configs/stage27/repository-governance.json`

It records:

- stable branch: `main`;
- canonical development branch: `feature/syntra-aetheris-foundation-v2`;
- release-to-main review requirement;
- prohibition on direct stage-branch development;
- retired branch names used as negative-test inputs;
- the exact canonical head allowed to target `main`;
- the reviewed `release/` head prefix allowed to target `main`;
- the pre-PC truth boundary.

The retired branch names remain in this policy intentionally. They are negative-test inputs used to prevent those branch names from being reintroduced into active CI/development workflows.

## Offline verifier

`scripts/stage27/repository_governance.py` is standard-library-only and deterministic.

It verifies:

1. the stable and canonical branch names have not changed unexpectedly;
2. the canonical branch is not accidentally marked legacy;
3. workflow files do not still trigger from retired Stage 24/25/26 or integration branches;
4. the README exposes the canonical development-line policy and physical-PC truth boundary;
5. a push validation run is occurring on the canonical development branch;
6. a pull request to `main` originates from either the canonical branch or a non-empty reviewed `release/*` branch;
7. unrelated feature/stage/integration branches cannot be used for release promotion;
8. a retired branch cannot be used for active development or release promotion.

The verifier does not call the GitHub API and does not pretend that CI policy is the same thing as GitHub branch protection or live-ref inventory.

## CI gate

`.github/workflows/stage27-repository-governance.yml` runs on:

- pushes to `feature/syntra-aetheris-foundation-v2`;
- pull requests targeting `main`.

The workflow compiles the verifier, runs fail-closed tests, evaluates the current GitHub Actions event context, reasserts the physical-PC truth boundary, and uploads a deterministic governance report.

## Promotion rule

For a pull request targeting `main`:

```text
base must equal main
head must equal feature/syntra-aetheris-foundation-v2
  OR
head must match release/<non-empty-name>
```

A `release/*` branch does not create a second development line. It exists only to make a narrow stable promotion reviewable when merging the entire canonical delta would include unrelated work.

Legacy stage/integration refs and arbitrary feature branches remain rejected by the Stage 27 CI gate.

This is a repository/CI safeguard. GitHub repository-level branch protection, rulesets, and automatic source-branch deletion are separate administrative controls and must not be claimed unless they are actually enabled.

## Physical-PC truth boundary

Stage 27 keeps:

`physical_pc_status = BLOCKED_PENDING_HARDWARE`

It makes no claim that WSL2, Docker Desktop, GPU acceleration, local models, sustained thermals, or the full local stack have passed physical-machine testing.

## Result

New PC-independent development continues on one canonical development branch:

`feature/syntra-aetheris-foundation-v2`

`main` remains the stable promotion target. Short-lived `release/*` branches are reviewable promotion carriers only and should be retired after merge. Live branch-count hygiene remains a separate repository-maintenance responsibility and must be measured from GitHub rather than inferred from this offline verifier.
