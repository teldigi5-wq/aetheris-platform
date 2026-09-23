# Contributing to Aetheris

Thanks for your interest in Aetheris.

Aetheris is a platform-engineering project with an independently owned AI runtime. Contributions should keep both repositories understandable, testable, secure, evidence-driven and easy to explain.

## Repository ownership

Before changing code, identify the source owner.

### `teldigi5-wq/aetheris-platform`

Owns the gateway, identity service, user service, audit service, dashboard, platform observability/deployment assets, cross-repository contracts, integration composition and platform-side evidence.

### `teldigi5-wq/aetheris-ai-runtime`

Owns `orchestrator-service`, `workstation-agent`, `aetheris-reasoning`, `aetheris-quant` and their runtime-owned certification assets.

Do not reintroduce runtime-owned roots into the platform as duplicate implementations. Cross-repository changes should update the contract/integration/evidence boundary deliberately rather than copying source between repositories.

## Development principles

- Prefer small, reviewable changes over large rewrites.
- Keep service, repository and trust boundaries explicit.
- Do not weaken authentication, authorization, validation, owner policy or observability for convenience.
- Avoid committing credentials, tokens, private keys, session files or production configuration.
- Add or update tests when behavior changes.
- Update documentation when architecture, APIs, governance, ownership or operational steps change.
- Never turn hosted CI into a claim of physical-PC validation.
- Models may assist implementation, but deterministic policy and repository governance remain authoritative.

## Branch and promotion workflow

Stable `main` is protected by repository governance.

The Stage 27 canonical development-line marker is:

```text
feature/syntra-aetheris-foundation-v2
```

For normal platform contributions:

1. Start from the current approved development base for the work.
2. Create a focused branch for one change.
3. Make the change and run relevant local checks.
4. Update documentation/evidence when required.
5. Open the appropriate pull request and wait for the applicable CI/governance checks.
6. Do not merge or promote a failing exact head.
7. Stable promotion to `main` must use an allowed reviewed path.

Stage 27 currently permits stable-main PR heads from the canonical development line or a non-empty reviewed `release/*` branch. A random feature/chore/docs branch must not be made to pass by weakening the governance policy; route the change through an allowed promotion path instead.

Do not use a temporary stage/chore branch as a new long-lived canonical base.

Runtime contributions follow the runtime repository's own review/certification path. A runtime change is not platform-certified merely because runtime CI passes; the platform certification reference is advanced separately after the required integration evidence.

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

- the change has a clear purpose and correct source owner;
- the affected repository still builds;
- relevant tests pass;
- no secrets or generated credentials are included;
- configuration changes have safe local defaults;
- new endpoints or flows are documented;
- security-sensitive changes describe their threat/permission impact;
- operational changes include rollback or recovery notes when appropriate;
- owner-policy or approval behavior has not been weakened accidentally;
- physical-machine claims remain evidence-based;
- platform changes do not restore runtime-owned source;
- runtime changes do not silently rewrite the platform's certified-runtime reference;
- the PR targets a policy-allowed review/promotion path.

## Architecture-sensitive changes

Platform changes to identity, gateway authorization, messaging, rate limiting, persistence, Kubernetes resources or observability should explain why the chosen approach fits the existing platform architecture.

Runtime changes involving workstation control, trading, reasoning, agent/tool execution or AI governance belong in `teldigi5-wq/aetheris-ai-runtime` unless the change is specifically to a platform/runtime contract or integration asset.

For larger changes, open an issue or design note first so trade-offs can be discussed before implementation.

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

Live-money execution remains outside the trusted default path. Physical owner-PC behavior remains `BLOCKED_PENDING_HARDWARE` until real hardware evidence exists.

## Local verification

For platform-owned source only, use the core composition:

```bash
docker compose -f docker-compose.core.yml up --build
```

When external AI-runtime integration is in scope, use the explicit external-runtime composition and exact intended runtime artifact/reference:

```bash
docker compose -f docker-compose.integration-external.yml up
```

Do not treat a mutable/latest runtime revision as the certified runtime by default.

For Kubernetes-specific work, follow `docs/kubernetes.md`.

For a reviewer-friendly end-to-end path, follow `docs/demo-guide.md`.

## Repository verification

Use the checks relevant to the surfaces you changed.

Platform evidence includes core backend/dashboard builds, CodeQL, core-source-independence, compatibility/release evidence and platform/external-runtime readiness gates. Runtime-owned regression, workstation, reasoning, trading and runtime-safety certification execute in `teldigi5-wq/aetheris-ai-runtime`.

Do not claim a change is fully validated until the relevant exact-head checks have completed successfully. Hosted workflow success remains repository evidence only; it does not prove production activation, registry publication, live-money execution or physical-PC readiness.

## Code review focus

Reviews should prioritize:

1. correctness;
2. security and owner control;
3. source/repository ownership;
4. failure behavior;
5. verification/evidence quality;
6. maintainability;
7. observability;
8. performance where it materially matters.

Aetheris is intended to demonstrate professional engineering habits as well as working software, so clear reasoning is valued as much as code volume.
