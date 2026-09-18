# Connector Integration Phase 7 — Approval-Gated Live Provider Writes

Phase 7 extends the Phase 6 connector-action boundary with an explicitly enabled live execution path for Gmail, Google Calendar, and GitHub. It does not make provider writes autonomous: every consequential action still creates an Aetheris task and HIGH-risk owner approval before any provider HTTP request can be sent.

## Supported live actions

- `GMAIL_SEND_EMAIL` — `targetRef` is the recipient email address. The approved action summary is used as the message subject/body for this foundation.
- `CALENDAR_CREATE_EVENT` — `targetRef` is `calendarId|start-rfc3339|end-rfc3339`. The approved action summary becomes the event summary.
- `GITHUB_CREATE_ISSUE` — `targetRef` is `owner/repository`. The approved action summary becomes the issue title/body and includes an Aetheris action marker.

## Safety model

Live execution is fail-closed by default. Two separate runtime opt-ins are required:

- `AETHERIS_CONNECTORS_LIVE_WRITES_ENABLED=true` enables the Phase 7 live dispatcher.
- `AETHERIS_CONNECTORS_WRITE_SCOPES_ENABLED=true` requests provider write-capable OAuth scopes.

Without both the explicit runtime opt-in and a valid ACTIVE OAuth credential containing the required provider write scope, live execution is blocked. Owner approval remains mandatory even when both flags are enabled.

Required scopes are:

- Gmail: `gmail.send` (or the full Gmail scope)
- Google Calendar: `calendar.events` (or the full Calendar scope)
- GitHub: `public_repo`, `repo`, or an equivalent `issues:write` scope

OAuth token material remains in the runtime credential vault and is not returned in action views or written into evidence reports.

## Idempotency and receipts

The existing `(connectionId, idempotencyKey)` action boundary is retained. Once an action has an `EXECUTED` receipt, replaying the local execute endpoint returns the persisted receipt without issuing another provider request. Provider-side exactly-once behavior is **not** claimed: a process/network failure after a provider accepts a request but before Aetheris persists the receipt can still require provider-specific reconciliation in a later phase.

## Hosted runtime proof

`Connector Live Write Phase 7 Runtime Proof` starts the real Dockerized orchestrator and PostgreSQL plus a local synthetic OAuth/provider server. The proof enables write scopes and live writes only inside that isolated CI environment, then demonstrates:

- write-capable OAuth credentials are issued for GitHub, Gmail, and Calendar;
- all three live actions are blocked before owner approval;
- the provider receives zero writes before approval;
- approved actions perform real HTTP POST requests with runtime Bearer credentials;
- provider-like receipts are persisted in Aetheris;
- local execution replay does not issue a second provider POST;
- approval/audit links remain intact; and
- no unrecognized provider mutations occur.

Evidence class: `HOSTED_RUNTIME_PROVIDER_WRITE_STUB`.

## Truth boundary

`HOSTED_RUNTIME_PROVIDER_WRITE_STUB` proves owner-approved LIVE-mode connector actions perform real HTTP POST requests using runtime OAuth credentials against a local synthetic provider stub. It does not prove mutation of production Gmail, Google Calendar or GitHub accounts, provider-side exactly-once guarantees, production secret-manager durability, unattended production autonomy, or physical-PC validation.

Production-provider live-write validation therefore remains separate from this hosted proof. Physical-PC validation remains `BLOCKED_PENDING_HARDWARE`.
