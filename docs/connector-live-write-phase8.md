# Connector Phase 8 — Credential-Gated Live Test-Account Write Validation

Phase 8 moves the Phase 7 provider-write path from a local provider stub to real provider APIs, but only against dedicated test accounts and only after an explicit arm signal.

## Status and truth boundary

The implementation can produce evidence class `LIVE_PROVIDER_TEST_ACCOUNT_WRITES` only when the live lane actually passes. Merely merging the Phase 8 code does **not** prove real provider mutation.

A passing Phase 8 live run proves that explicitly armed, owner-approved Aetheris connector actions can create test data through the real Gmail, Google Calendar, and GitHub provider APIs using repository-scoped test-account credentials. The stored evidence is sanitized and does not persist tokens, provider response bodies, account identifiers, target values, or provider message/event/issue identifiers.

It does **not** prove provider-side exactly-once guarantees, safety for production accounts, production secret-manager durability, unattended production autonomy, or physical-PC validation. Physical-PC validation remains `BLOCKED_PENDING_HARDWARE`.

## Mutations performed

A successful run performs exactly one approved validation action for each connector type:

- Gmail: sends one test email.
- Google Calendar: creates one short test event in the configured test calendar.
- GitHub: creates one test issue in the configured test repository.

The harness also attempts execution before approval and requires all three actions to be blocked. It then replays the Gmail action after successful execution and requires Aetheris to return the persisted receipt with `replay=true` rather than dispatching a new local execution.

Provider-side exactly-once behavior is intentionally **not** claimed.

## Required repository secrets

Use credentials belonging to dedicated test accounts and test resources only. Do not use primary personal, school, employer, or production credentials.

Phase 8 uses a dedicated GitHub write token so the earlier Phase 5 read-only GitHub credential remains read-only. Gmail and Calendar continue to use the existing live test-account access-token secrets. Provider tokens are mapped into the Phase 8 broker only inside the ephemeral GitHub Actions runner.

| Secret | Purpose |
| --- | --- |
| `AETHERIS_PHASE8_GITHUB_ACCESS_TOKEN` | Dedicated fine-grained token restricted to the Phase 8 test repository with Issues read/write permission. |
| `AETHERIS_LIVE_GMAIL_ACCESS_TOKEN` | Google OAuth access token containing `gmail.send`. |
| `AETHERIS_LIVE_CALENDAR_ACCESS_TOKEN` | Google OAuth access token containing `calendar.events`. |
| `AETHERIS_PHASE8_TEST_GMAIL_RECIPIENT` | Test recipient email address. |
| `AETHERIS_PHASE8_TEST_CALENDAR_ID` | Test calendar ID (for a dedicated test account, `primary` is acceptable). |
| `AETHERIS_PHASE8_TEST_GITHUB_REPOSITORY` | Separate `owner/repository` used only for test issues. |
| `AETHERIS_PHASE8_ARM_CONFIRMATION` | Must equal `I_UNDERSTAND_PHASE8_CREATES_LIVE_TEST_DATA`. |

The live validator refuses `teldigi5-wq/aetheris-platform` as the GitHub mutation target. The dedicated Phase 8 GitHub token should likewise exclude that repository and be scoped only to the separate test repository.

Google access tokens are normally short-lived. A stale token should fail the Phase 8 run rather than silently falling back to synthetic evidence.

## Credential handling

The GitHub Actions job gives the three provider access tokens only to `tools/connector_live_write_phase8_token_broker.py`. The broker runs locally on the ephemeral runner and never logs token material. Aetheris exchanges a synthetic local authorization code with this broker, then stores the returned provider access token in the existing in-memory non-exported credential vault.

The orchestrator container does not receive the provider tokens as environment variables. Provider writes then leave Aetheris through the real provider endpoints configured in `connector-live-write-phase8-compose.override.yml`.

## Execution gates

The live workflow is `.github/workflows/connector-live-write-phase8-live.yml`.

It has no `pull_request` trigger. Ordinary pushes to the Phase 8 branch execute only the disarmed gate job. The mutation job can run only when either:

1. the workflow is explicitly dispatched after it is available on the repository default branch, or
2. while `main` remains intentionally untouched, a commit on `feature/connector-live-test-write-phase8` contains the exact marker `[phase8-live-write]` **and** every required Phase 8 repository secret is configured.

The arm marker alone is insufficient. Missing credentials, missing targets, a wrong arm-confirmation secret, or use of the real Aetheris project repository as the GitHub target causes the live lane to fail closed before provider mutation.

A dedicated acceptance-trigger commit may contain the arm marker solely to request this gated proof. The credential gate remains authoritative: the run must fail closed before any provider request whenever a required Phase 8 secret or test target is absent.

After any executor, credential-boundary, or provider-error-handling change, the exact-head non-mutating CI must pass again before another armed acceptance rerun.

### Readiness evidence

Every armed run writes `connector-live-write-phase8-readiness.json` before any provider mutation. The report records only the names of missing configuration items, never their values. When configuration is incomplete it reports `BLOCKED_MISSING_CONFIGURATION`, `provider_mutation_started=false`, and preserves `BLOCKED_PENDING_HARDWARE` for physical-PC validation. This readiness report is uploaded even when the live proof fails closed.

When an armed provider execution fails, the sanitized report may additionally record only the failed provider stage plus numeric orchestrator/provider HTTP status codes when available. It never persists the provider response body, target, token, account identifier, or provider-created object identifier.

## Non-mutating CI proof

`.github/workflows/connector-live-write-phase8-contract.yml` is safe for normal PR and push CI. It compiles the Phase 8 harnesses, validates the exact 20-check contract, verifies the live workflow has no pull-request trigger, confirms all required secret gates are present, and confirms the compose override points only to the expected real provider hosts.

This CI check proves the **validation machinery and safety gates**, not a live provider mutation.

## Acceptance sequence

Phase 8 should be accepted in this order:

1. exact-head non-mutating contract CI passes on the Phase 8 PR;
2. code review confirms no live-provider secret or target is committed;
3. dedicated test-account secrets are configured outside git;
4. an explicitly armed live run passes all 20 checks and publishes only the sanitized report;
5. the Phase 8 PR is merged into `feature/syntra-aetheris-foundation-v2`;
6. canonical post-merge CI is fully green before any later connector phase begins.

`main` remains outside this work.
