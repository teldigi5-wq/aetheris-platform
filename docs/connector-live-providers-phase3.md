# Connector Live Providers — Phase 3

Phase 3 adds the credential-ready OAuth foundation that sits between the provider-neutral connector core and real external accounts.

## Implemented providers

- GitHub OAuth foundation with read-only identity scopes (`read:user`, `user:email`).
- Google OAuth foundation for Gmail with `gmail.readonly` plus OpenID identity scopes.
- Google OAuth foundation for Calendar with `calendar.readonly` plus OpenID identity scopes.

The same lifecycle is intentionally reusable for Microsoft/Outlook and later provider adapters.

## Security model

1. A cryptographically random OAuth `state` value is returned to the caller once. Only its SHA-256 digest is persisted.
2. PKCE uses S256. The verifier is kept only in the runtime credential vault and PostgreSQL stores an opaque vault reference.
3. Access and refresh token values are never returned by the API and are never persisted in connector tables. Only opaque vault references and lifecycle metadata are persisted.
4. Authorization sessions are single-use and expire after ten minutes.
5. Provider health checks are read-only Bearer identity calls.
6. Refresh rotates runtime access-token material and drops superseded vault entries.
7. Disconnect deletes runtime token material, clears persisted token references, marks the credential `REVOKED`, and suspends the connector locally.
8. Phase 3 does not add provider-side writes, email sends, calendar writes, GitHub mutations, deployment actions, billing actions, or permission bypasses.

## Runtime configuration

Real providers require runtime environment configuration. No client secret is committed to the repository.

GitHub:
- `AETHERIS_OAUTH_GITHUB_CLIENT_ID`
- `AETHERIS_OAUTH_GITHUB_CLIENT_SECRET`
- optional endpoint overrides for authorization, token and user-info URLs

Google:
- `AETHERIS_OAUTH_GOOGLE_CLIENT_ID`
- `AETHERIS_OAUTH_GOOGLE_CLIENT_SECRET`
- optional endpoint overrides for authorization, token and user-info URLs

If the required runtime client configuration is absent, authorization fails closed with HTTP 503.

## API surface

- `POST /api/orchestrator/connectors/connections/{connectionId}/oauth/authorizations`
- `POST /api/orchestrator/connectors/oauth/authorizations/{sessionId}/complete`
- `GET /api/orchestrator/connectors/connections/{connectionId}/oauth/credential`
- `POST /api/orchestrator/connectors/connections/{connectionId}/oauth/refresh`
- `GET /api/orchestrator/connectors/connections/{connectionId}/provider-health`
- `DELETE /api/orchestrator/connectors/connections/{connectionId}/oauth/credential`

## Hosted runtime proof

The Phase 3 GitHub Actions proof starts a local synthetic OAuth provider and the real Dockerized orchestrator service. The orchestrator performs real HTTP authorization-code exchange, PKCE/state validation, Bearer identity checks, refresh and local revocation against that provider.

The proof is `HOSTED_RUNTIME` evidence only. It does **not** prove a live GitHub OAuth App, Google Cloud OAuth consent screen, production Gmail/Calendar data access, Microsoft/Meta accounts, production secret-manager durability, public ingress hardening, provider SLA behavior, or physical-PC behavior.

Physical-PC validation remains `BLOCKED_PENDING_HARDWARE`.
