# Connector Integration Layer — Phase 1

Aetheris now has a provider-neutral connector ingestion layer for routing external-system events into Syntra's proactive executive-agent workflow without coupling the platform to one vendor.

## What Phase 1 implements

- persistent connector connection metadata with owner, provider, external account reference, status and declared capabilities;
- provider identifiers for Gmail, Outlook, Slack, Discord, WhatsApp, Instagram, GitHub, Calendar, Stripe, Vercel and generic webhooks;
- explicit `REGISTERED`, `ENABLED` and `SUSPENDED` lifecycle states;
- capability enforcement before an event may enter the executive agent;
- normalized provider/account source identity generated from the registered connection rather than caller-supplied free text;
- replay protection using the unique `(connectionId, externalEventId)` pair;
- persisted connector receipts linking the provider event to the resulting executive run, disposition, task and approval IDs;
- trust downgrade: a caller-requested low-risk flag is effective only for message-like signals whose upstream delivery is marked verified;
- routing into the existing proactive executive agent, including the existing owner-approval gate for billing and deployment actions;
- runtime proof covering registration, enablement, trust downgrade, replay safety, persistence and approval visibility.

## API

Base path: `/api/orchestrator/connectors`

- `POST /connections` — register provider/account metadata and inbound capabilities.
- `GET /connections` — inspect recent connector registrations.
- `PATCH /connections/{id}/status` — explicitly enable or suspend ingestion.
- `POST /events` — ingest one normalized external event.
- `GET /receipts` — inspect recent replay-safe event receipts.

## Safety and privacy boundary

Phase 1 intentionally stores **no passwords, OAuth access tokens, OAuth refresh tokens, API secrets or provider credentials**. `ENABLED` means that Aetheris will accept internal normalized deliveries for that connection; it does **not** mean that a live provider OAuth session has been authenticated.

There is no `SEND_MESSAGES` capability in Phase 1. Untrusted external messages are drafted for owner review by the executive agent, and consequential billing/deployment events continue to create normal Aetheris approval requests.

The `deliveryVerified` flag is an assertion supplied by a future provider adapter. In this phase it is not cryptographic proof of a Gmail/Meta/Slack/etc. webhook signature. Real provider adapters must verify provider signatures/tokens before setting it true.

## Runtime evidence boundary

The hosted proof uses synthetic provider deliveries against the real orchestrator service and real persistence/approval surfaces. It proves the connector core's registry, capability gate, normalization, deduplication, trust downgrade and executive routing behavior.

It does **not** prove:

- live Gmail/Outlook/Slack/Discord/WhatsApp/Instagram OAuth connectivity;
- provider-native webhook signature verification;
- external message sending;
- calendar mutation;
- telephony;
- production provider connectivity;
- production scheduling or provider SLAs;
- physical-PC validation.

Physical-PC validation remains `BLOCKED_PENDING_HARDWARE`.

## Planned Phase 2

Phase 2 can add concrete adapters one provider at a time. Each adapter should own provider authentication, least-privilege scopes, webhook/poll verification, rate-limit/backoff behavior, token storage through a secrets boundary, and translation into the Phase 1 event contract. Outbound actions should remain draft/approval gated until separately proven.
