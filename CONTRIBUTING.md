# Contributing to Aetheris

Thanks for your interest in Aetheris.

Aetheris is a learning-focused platform-engineering project built around real distributed-systems concepts. Contributions should keep the codebase understandable, testable, secure and easy to explain.

## Development principles

- Prefer small, reviewable changes over large rewrites.
- Keep service boundaries explicit.
- Do not weaken authentication, authorization, validation or observability for convenience.
- Avoid committing credentials, tokens, private keys, session files or production configuration.
- Add or update tests when behavior changes.
- Update documentation when architecture, APIs or operational steps change.

## Suggested workflow

1. Create a branch from `main`.
2. Make one focused change.
3. Run the relevant tests and local checks.
4. Update documentation where needed.
5. Open a pull request explaining the problem, approach and verification performed.

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
- operational changes include rollback or recovery notes when appropriate.

## Architecture-sensitive changes

Changes to identity, gateway authorization, messaging, rate limiting, persistence, Kubernetes resources or observability should explain why the chosen approach fits the existing architecture.

For larger changes, open an issue or design note first so the trade-offs can be discussed before implementation.

## Local verification

The core platform can be started with Docker Compose:

```bash
docker compose up --build
```

The full observability profile can be started with:

```bash
docker compose --profile observability up --build
```

For Kubernetes-specific work, follow the runbook in `docs/kubernetes.md`.

## Code review focus

Reviews should prioritize:

1. correctness;
2. security;
3. failure behavior;
4. maintainability;
5. observability;
6. performance where it materially matters.

Aetheris is intended to demonstrate professional engineering habits as well as working software, so clear reasoning is valued as much as code volume.
