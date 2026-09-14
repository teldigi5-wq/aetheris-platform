# Contributing to Aetheris

Thanks for your interest in Aetheris.

Aetheris is a platform-engineering and local-AI governance project built around real distributed-systems concepts. Contributions should keep the codebase understandable, testable, secure, evidence-driven and easy to explain.

## Development principles

- Prefer small, reviewable changes over large rewrites.
- Keep service and trust boundaries explicit.
- Do not weaken authentication, authorization, validation, owner policy or observability for convenience.
- Avoid committing credentials, tokens, private keys, session files or production configuration.
- Add or update tests when behavior changes.
- Update documentation when architecture, APIs, governance or operational steps change.
- Never turn hosted CI into a claim of physical-PC validation.
- Models may assist implementation, but deterministic policy and repository governance remain authoritative.

## Branch and promotion workflow

Stable `main` is protected by the repository ruleset **`Protect stable main`**.

The canonical development line is:

```text
feature/syntra-aetheris-foundation-v2
```

For normal contributions:

1. Start from the current canonical development line.
2. Create a focused branch for one change.
3. Make the change and run relevant local checks.
4. Update documentation/evidence when required.
5. Open a pull request **into `feature/syntra-aetheris-foundation-v2`**.
6. Wait for the applicable CI/governance checks to pass.
7. Merge the validated change into the canonical development line.
8. Stable promotion to `main` happens through a separate pull request from the canonical development line and must satisfy the protected-main checks.

Do not use a temporary stage/chore branch as a new long-lived canonical base.

## Commit style

Use concise conventional-style messages when practical:

```text
feat: add developer API key flow
fix: handle expired refresh token rotation
refactor: simplify gateway scope validation
test: cover user-service cache eviction
docs: update Kubernetes runbook
chore: update local development tooling
```

## Pull request checklist

Before opening a pull request, verify that:

- the change has a clear purpose;
- the project still builds;
- relevant tests pass;
- no secrets or generated credentials are included;
- configuration changes have safe local defaults;
- new endpoints or flows are documented;
- security-sensitive changes describe their threat/permission impact;
- operational changes include rollback or recovery notes when appropriate;
- owner-policy or approval behavior has not been weakened accidentally;
- physical-machine claims remain evidence-based;
- the PR targets the correct canonical branch.

## Architecture-sensitive changes

Changes to identity, gateway authorization, messaging, rate limiting, persistence, workstation control, trading, agent/tool execution, Kubernetes resources or observability should explain why the chosen approach fits the existing architecture.

For larger changes, open an issue or design note first so the trade-offs can be discussed before implementation.

## Safety-sensitive changes

Changes involving destructive actions, privileged host operations, financial actions, public side effects, Private/Zero-Cost behavior, emergency control or scoped approvals must preserve the core governance semantics:

```text
UNDERSTAND
  → PLAN
  → CHECK RULES
  → ASSESS RISK
  → SIMULATE / PREVIEW when required
  → APPROVE when required
  → EXECUTE
  → VERIFY
  → RECORD
  → LEARN
  → REPORT
```

`ALLOW` means eligible to execute; it does not prove execution. A successful result requires verification evidence or an explicit `UNVERIFIED` outcome.

## Local verification

The core platform can be started with Docker Compose:

```bash
docker compose up --build
```

The full observability profile can be started with:

```bash
docker compose --profile observability up --build
```

For Kubernetes-specific work, follow `docs/kubernetes.md`.

For a reviewer-friendly end-to-end path, follow `docs/demo-guide.md`.

## Repository verification

Use the checks relevant to the surfaces you changed. The repository includes Java, dashboard, workstation, CodeQL, dependency-lockdown/reproducibility and Stage 25–34 validation workflows.

Do not claim a change is fully validated until the relevant checks have completed successfully. Hosted workflow success remains repository evidence only.

## Code review focus

Reviews should prioritize:

1. correctness;
2. security and owner control;
3. failure behavior;
4. verification/evidence quality;
5. maintainability;
6. observability;
7. performance where it materially matters.

Aetheris is intended to demonstrate professional engineering habits as well as working software, so clear reasoning is valued as much as code volume.
