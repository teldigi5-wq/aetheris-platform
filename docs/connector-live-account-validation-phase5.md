# Connector Integration Phase 5 — Live Provider Validation

Phase 5 adds a production-facing **read-only validation lane** for GitHub, Gmail, and Google Calendar credentials. It does not add provider-side writes.

## What CI proves

Normal pull-request and canonical push CI runs a local synthetic provider over real HTTP and validates that the Phase 5 verifier:

- performs only `GET` requests;
- accepts GitHub, Gmail, and Calendar read shapes;
- enforces a provider-host allowlist;
- never persists provider response bodies;
- never persists account identifiers, repository names, email addresses, or event titles;
- never persists access-token material;
- emits sanitized counts/status only;
- records zero provider mutations.

This evidence remains `HOSTED_RUNTIME`; it does **not** claim real production-account access.

## Manual live validation

The canonical live-validation workflow is `.github/workflows/connector-live-account-phase5-manual.yml`. It is `workflow_dispatch` only and is started explicitly from the protected default branch when a fresh read-only provider proof is needed. It requires these GitHub Actions secrets:

- `AETHERIS_LIVE_GITHUB_ACCESS_TOKEN`
- `AETHERIS_LIVE_GMAIL_ACCESS_TOKEN`
- `AETHERIS_LIVE_CALENDAR_ACCESS_TOKEN`

Use short-lived tokens with the smallest read-only scopes possible. The GitHub token should allow user/profile and public-repository reads. Gmail must have `gmail.readonly`. Calendar must have `calendar.readonly`.

The live workflow contacts only these production hosts:

- `api.github.com`
- `gmail.googleapis.com`
- `www.googleapis.com`

If a base URL points elsewhere, live mode fails closed.

The historical `validation/connector-live-phase5` branch is not part of the current execution contract. Its branch-only commits are validation trigger markers rather than product implementation, and the old branch-specific workflow has been retired in favor of the existing manual workflow. Once this change has merged through protected `main`, the exact `main` and canonical-development push suites are green, and the branch lifecycle audit records that evidence, the historical Phase 5 validation branch can be deleted without removing the Phase 5 capability or its manual rerun path.

## Evidence boundary

A successful manual run can prove:

- real GitHub identity and public-repository read access;
- real Gmail message-list read access;
- real Google Calendar event-list read access;
- sanitized live-provider evidence with no stored PII or token material.

It still does not prove or enable:

- email sends;
- calendar writes;
- GitHub mutations;
- deployment or billing actions;
- provider SLA behavior;
- physical-PC validation.

Physical-PC validation remains `BLOCKED_PENDING_HARDWARE`.

## Rerun discipline

A live Phase 5 rerun must remain an explicit operator action. A branch push, pull request, or ordinary canonical push must never perform provider-account access automatically. Before dispatching the manual workflow, confirm the configured credentials are dedicated to the intended read-only validation scope and that no provider-write permission has been introduced.

Changes to the verifier, provider allowlist, credential handling, report sanitization, or manual workflow must first pass the repository's normal protected-main review and exact-head CI gates. The presence of the workflow alone does not claim that a new live-provider validation has occurred.

## Syntra / Aetheris boundary

This remains **Aetheris connector infrastructure**. Syntra can consume the resulting normalized read-only provider signals through the existing Executive Agent path, but Syntra is not the credential store or provider integration runtime.
