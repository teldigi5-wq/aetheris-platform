# Connector Integration Layer — Phase 4: Read-Only Provider Data Synchronization

Phase 4 converts the Phase 3 credential-ready OAuth foundation into a real read-only synchronization path for GitHub, Gmail, and Google Calendar.

## Runtime flow

1. A connector connection is registered with the capability required for the provider.
2. Phase 3 OAuth authorization stores token material only in the runtime credential vault and persists references.
3. `POST /api/orchestrator/connectors/connections/{connectionId}/sync` reads the provider using the active access-token reference.
4. Provider objects are normalized into existing Aetheris `ConnectorInboundEvent` values.
5. The existing connector receipt layer deduplicates stable provider event IDs.
6. New items are fed into the Proactive Executive Agent through the existing ingestion pipeline.

## Provider normalization

- **Gmail**: message metadata and snippets become `EMAIL` signals. They are not trusted for automatic external action, so the Executive Agent keeps them in owner-review/draft behavior.
- **GitHub**: repository `pushed_at` observations become `CODE_UPDATE` signals. A provider read is not treated as repeated verification, so the Executive Agent escalates them for review rather than claiming a verified code update.
- **Google Calendar**: upcoming events become `CALENDAR_EVENT` signals and are tracked informationally with no provider-side action.

## Data minimization

Gmail synchronization reads only message metadata/snippets needed for triage. Calendar synchronization reads event timing and summary data. GitHub synchronization uses repository update metadata. The sync result contains normalized ingestion results and bounded errors, never bearer tokens, refresh tokens, PKCE verifiers, or credential-vault values.

## Idempotency

Stable external receipt IDs are derived with SHA-256 from provider identifiers and provider update versions:

- Gmail: message ID
- GitHub: repository ID + `pushed_at`
- Calendar: event ID + `updated`

Repeating a sync against unchanged provider data returns duplicates from the existing receipt layer and does not run the Executive Agent a second time for those items.

## Safety boundary

Phase 4 is read-only. It does **not** add email sending, calendar creation/modification, GitHub writes, deployment actions, billing actions, or other provider-side mutations. Existing Phase 3 scopes remain read-only.

## Hosted proof

The hosted proof runs the real Dockerized orchestrator and PostgreSQL against a local HTTP provider stub with ephemeral OAuth credentials. It checks GitHub/Gmail/Calendar reads, normalization, Executive Agent routing, persistence, idempotent repeated sync, token non-exposure, fail-closed behavior, and zero provider mutations.

This proof does not claim live production provider accounts or physical-PC validation. Physical validation remains `BLOCKED_PENDING_HARDWARE`.
