# GitHub Administrative Branch Protection Target

Stage 27 enforces repository/CI governance, but GitHub administrative branch rules are a separate control.

Current observed repository state on 2026-09-14:

- `main`: GitHub reports `protected=false`.
- `feature/syntra-aetheris-foundation-v2`: GitHub reports `protected=false`.
- repository rulesets API returns no configured rulesets.
- the connected GitHub integration cannot administer branch protection; the protection endpoint returns `403 Resource not accessible by integration`.

Nothing in CI should claim these administrative protections are enabled until GitHub itself reports them enabled.

## Recommended rule for `main`

In GitHub: **Settings → Rules → Rulesets → New branch ruleset** (or Branch protection rules if Rulesets are unavailable).

Target `main` and configure:

- restrict deletions;
- block force pushes;
- require a pull request before merge;
- require conversation resolution before merge;
- require status checks to pass before merge;
- require the branch to be up to date before merge when practical;
- do not permit bypass for ordinary development work.

Select the stable CI checks after they have produced current check names, including the repository Build, dependency/reproducibility hardening, Stage 25 readiness, Stage 26 safety/evidence certification, Stage 27 repository governance and Stage 28 PC-care certification.

For a solo repository, requiring one approving review can lock the owner out of self-merging because authors cannot approve their own PR. Keep the PR requirement and required status checks, but only require an external approving review when a trusted collaborator/reviewer is available.

## Recommended rule for the canonical development branch

Target:

`feature/syntra-aetheris-foundation-v2`

Configure at minimum:

- restrict deletions;
- block force pushes.

Do **not** require every development update to arrive from another temporary branch while this repository intentionally uses one canonical development line. The CI suites run on pushes to this branch and PRs toward `main`.

## Promotion contract

Stage 27 additionally requires that a PR targeting `main` originates from:

`feature/syntra-aetheris-foundation-v2`

That CI rule complements GitHub administrative protection; it does not replace it.

## Verification after enabling

After the owner enables the rules in GitHub Settings, verify the branch/ruleset UI and GitHub API report protection as active. Until that happens, documentation must continue to describe administrative protection as **pending**.
