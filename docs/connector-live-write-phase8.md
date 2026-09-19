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

Phase 8 uses a dedicated GitHub write token so the earlier Phase 5 read-only GitHub credential remains read-only.

Always-required secrets:

| Secret | Purpose |
| --- | --- |
| `AETHERIS_PHASE8_GITHUB_ACCESS_TOKEN` | Dedicated fine-grained token restricted to the Phase 8 test repository with Issues read/write permission. |
| `AETHERIS_PHASE8_TEST_GMAIL_RECIPIENT` | Test recipient email address. |
| `AETHERIS_PHASE8_TEST_CALENDAR_ID` | Test calendar ID (for a dedicated test account, `primary` is acceptable). |
| `AETHERIS_PHASE8_TEST_GITHUB_REPOSITORY` | Separate `owner/repository` used only for test issues. |
| `AETHERIS_PHASE8_ARM_CONFIRMATION` | Must equal `I_UNDERSTAND_PHASE8_CREATES_LIVE_TEST_DATA`. |

The live validator refuses `teldigi5-wq/aetheris-platform` as the GitHub mutation target. The dedicated Phase 8 GitHub token should likewise exclude that repository and be scoped only to the separate test repository.

## Preferred Google credential mode — automatic refresh

Google access tokens are short-lived, so the preferred Phase 8 configuration stores a refresh token plus its OAuth client credentials. At the start of every armed run, GitHub Actions exchanges the refresh token at `https://oauth2.googleapis.com/token`, masks the returned access token, and keeps that fresh access token only inside the ephemeral runner environment.

Configure all three secrets together:

| Secret | Purpose |
| --- | --- |
| `AETHERIS_PHASE8_GOOGLE_REFRESH_CLIENT_ID` | OAuth client ID that issued the refresh token. |
| `AETHERIS_PHASE8_GOOGLE_REFRESH_CLIENT_SECRET` | OAuth client secret paired with that client ID. |
| `AETHERIS_PHASE8_GOOGLE_REFRESH_TOKEN` | Offline refresh token authorized for both `gmail.send` and `calendar.events`. |

The refresh token must have been granted with both scopes:

- `https://www.googleapis.com/auth/gmail.send`
- `https://www.googleapis.com/auth/calendar.events`

Request offline access during the one-time Google authorization so a refresh token is issued. A refresh token is normally returned on the first consent grant; re-authorization with an explicit consent prompt may be needed if one was not returned.

For external Google OAuth apps left in **Testing**, Google may expire refresh tokens after seven days when non-basic scopes are involved. For a durable personal test integration, use an OAuth configuration whose publishing/verification state is appropriate for the account and scopes rather than relying on endlessly regenerated one-hour access tokens.

### Legacy fallback

The earlier short-lived access-token mode remains available as a fallback. If the refresh-token trio above is not configured, both of these must be present:

| Secret | Purpose |
| --- | --- |
| `AETHERIS_LIVE_GMAIL_ACCESS_TOKEN` | Short-lived Google OAuth access token containing `gmail.send`. |
| `AETHERIS_LIVE_CALENDAR_ACCESS_TOKEN` | Short-lived Google OAuth access token containing `calendar.events`. |

The live lane prefers the refresh-token mode whenever the complete refresh-token trio exists. Legacy access-token secrets can therefore remain in the repository during migration without being used.

## Credential handling

The GitHub Actions job resolves the Google credential mode before starting the local Phase 8 broker. In refresh-token mode it obtains one fresh Google access token at runtime and maps it to both Gmail and Calendar because the stored refresh grant covers both required scopes. In legacy mode it maps the two existing short-lived access tokens instead.

Only the resolved provider access tokens are handed to `tools/connector_live_write_phase8_token_broker.py`. The broker runs locally on the ephemeral runner and never logs token material. Aetheris exchanges a synthetic local authorization code with this broker, then stores the returned provider access token in the existing in-memory non-exported credential vault.

The orchestrator container does not receive provider tokens as environment variables. Provider writes then leave Aetheris through the real provider endpoints configured in `connector-live-write-phase8-compose.override.yml`.

Refresh tokens, OAuth client secrets, provider access tokens, provider response bodies, and target values are never written into the sanitized Phase 8 evidence.

## Execution gates

The live workflow is `.github/workflows/connector-live-write-phase8-live.yml`.

It has no `pull_request` trigger. Ordinary pushes to the Phase 8 branch execute only the disarmed gate job. The mutation job can run only when either:

1. the workflow is explicitly dispatched after it is available on the repository default branch, or
2. while `main` remains intentionally untouched, a commit on `feature/connector-live-test-write-phase8` contains the exact marker `[phase8-live-write]` **and** every required Phase 8 repository setting is configured.

The arm marker alone is insufficient. Missing credentials, missing targets, a wrong arm-confirmation secret, or use of the real Aetheris project repository as the GitHub target causes the live lane to fail closed before provider mutation.

For Google authentication, the gate accepts exactly one complete credential mode: the preferred refresh-token trio or the two legacy access-token secrets. A partially configured mode does not count as ready.

A dedicated acceptance-trigger commit may contain the arm marker solely to request this gated proof. The credential gate remains authoritative: the run must fail closed before any provider request whenever required Phase 8 configuration is absent.

After any executor, credential-boundary, provider-error-handling, or refresh-flow change, the exact-head non-mutating CI must pass again before another armed acceptance rerun.

### Readiness evidence

Every armed run writes `connector-live-write-phase8-readiness.json` before any provider mutation. The report records only missing configuration names and the selected Google credential mode, never secret values. When configuration is incomplete it reports `BLOCKED_MISSING_CONFIGURATION`, `provider_mutation_started=false`, and preserves `BLOCKED_PENDING_HARDWARE` for physical-PC validation. This readiness report is uploaded even when the live proof fails closed.

When an armed provider execution fails, the sanitized report may additionally record only the failed provider stage plus numeric orchestrator/provider HTTP status codes when available. It never persists the provider response body, target, token, account identifier, or provider-created object identifier.

## Non-mutating CI proof

`.github/workflows/connector-live-write-phase8-contract.yml` is safe for normal PR and push CI. It compiles the Phase 8 harnesses, validates the exact 20-check contract, verifies the live workflow has no pull-request trigger, confirms both Google credential modes are explicitly wired, and confirms the compose override points only to the expected real provider hosts.

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
