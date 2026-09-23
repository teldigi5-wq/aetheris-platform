# GitHub Administrative Branch Protection State

Stage 27 enforces repository/CI promotion governance, while GitHub Rulesets provide the separate administrative protection applied by GitHub itself.

## Current observed state

The repository currently has an **active** branch ruleset:

```text
Protect stable main
```

Ruleset ID: `23315877`

It targets the default branch and currently enforces:

- branch deletion protection;
- non-fast-forward / force-push protection;
- pull requests before merge;
- review-thread resolution;
- strict required status checks before merge.

The repository therefore must **not** be described as having no administrative branch protection. That was an older pre-ruleset observation and is no longer current.

## Stage 27 promotion contract

The repository-side Stage 27 verifier distinguishes the stable branch from the long-lived development marker:

```text
stable: main
canonical development marker: feature/syntra-aetheris-foundation-v2
```

For a pull request targeting `main`, Stage 27 currently permits either:

- the canonical development branch itself; or
- a reviewed branch using the `release/*` prefix.

Legacy stage branches are not valid active development/promotion heads. The reviewed `release/*` path is intentional and was used successfully for the post-extraction documentation promotions.

Stage 27 complements GitHub's administrative ruleset; it does not replace it.

## Required-status drift after AI-runtime extraction

The active `Protect stable main` ruleset was configured before the AI-runtime extraction. Its current required-status list still includes these contexts:

```text
canonical-development-line
readiness-contract
backend
dashboard
workstation-agent
Analyze Java
Analyze JavaScript/TypeScript
stage26-certification
pc-care-simulation
dependency-lockdown
reproducible-build
```

Some of those names no longer match the current platform repository ownership or job names.

### Known stale mappings

| Ruleset context | Current state | Required administrative action |
|---|---|---|
| `backend` | platform Build now uses `core-backend` | replace the old required context with the current platform job |
| `Analyze Java` | CodeQL now uses `Analyze Core Java` | replace the old CodeQL context |
| `workstation-agent` | source/check is AI-runtime-owned after extraction | remove from the platform ruleset; enforce it in the runtime repository when appropriate |
| `pc-care-simulation` | Stage 28 PC-care certification is AI-runtime-owned after extraction | remove from the platform ruleset; enforce runtime certification in the runtime repository when appropriate |

Contexts such as `canonical-development-line`, `readiness-contract`, `dashboard`, `Analyze JavaScript/TypeScript`, `stage26-certification`, `dependency-lockdown` and `reproducible-build` remain platform-relevant where the corresponding workflow continues to emit that exact check name.

The platform Build also now carries source-ownership/release evidence such as `core-source-independence` and `release-hardening`. Whether those are made administratively required should be decided from the current exact check suite rather than by preserving obsolete names.

## Administrative alignment required before release publication

Before publishing `v0.1.0-pre-pc`, update **Settings → Rules → Rulesets → Protect stable main** so required status checks reflect the post-extraction platform suite.

The intended correction is administrative configuration, **not** a CI workaround. Do not add fake `backend`, `workstation-agent`, `pc-care-simulation` or other compatibility jobs merely to satisfy stale ruleset names.

After editing the ruleset:

1. open a fresh reviewed `release/*` pull request to `main`;
2. confirm the exact current required checks are emitted on that PR head;
3. confirm GitHub reports the PR mergeable only after those checks pass;
4. verify no runtime-owned platform-local check is required;
5. preserve strict required-status enforcement and PR review/thread-resolution behavior.

## Why runtime checks are separate now

Runtime-owned source lives in `teldigi5-wq/aetheris-ai-runtime`, including:

- `orchestrator-service/`;
- `workstation-agent/`;
- `aetheris-reasoning/`;
- `aetheris-quant/`.

The platform records an exact certified runtime checkpoint in `architecture/ai-runtime-certification-reference.json`. Platform branch protection should enforce platform and cross-repository boundary evidence, while runtime repository governance should enforce runtime-owned regression and safety certification.

Do not restore extracted runtime source or duplicate runtime jobs into the platform merely to simplify branch-protection configuration.

## Truth boundary

Administrative branch protection is evidence about repository change control. It does not prove physical-PC validation, production activation, registry publication or live-money execution.

Physical-machine status remains `BLOCKED_PENDING_HARDWARE` until real target-machine validation evidence exists.
