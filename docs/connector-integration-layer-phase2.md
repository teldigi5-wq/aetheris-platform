# Connector Integration Layer — Phase 2: Verified Webhook Adapters

Phase 2 adds real cryptographic inbound-delivery verification on top of the provider-neutral Phase 1 connector core. The goal is to prove that Aetheris can distinguish an authenticated webhook delivery from caller-supplied trust claims before Syntra's executive-agent workflow sees the event.

## Added adapters

### Generic HMAC-SHA256 webhook

Endpoint: `POST /api/orchestrator/connectors/webhooks/generic/{connectionId}`

Required headers:

- `X-Aetheris-Event-Id` — replay/idempotency identity;
- `X-Aetheris-Timestamp` — Unix epoch seconds;
- `X-Aetheris-Signature` — `sha256=<hex HMAC>`.

Signature input is exactly:

`<timestamp>.<raw request body>`

The adapter rejects stale/future timestamps outside the configured freshness window before ingestion. The default window is 300 seconds and allowed configuration is 30–900 seconds.

### GitHub HMAC-SHA256 webhook

Endpoint: `POST /api/orchestrator/connectors/webhooks/github/{connectionId}`

Required headers:

- `X-GitHub-Delivery` — reused as the replay/idempotency identity;
- `X-GitHub-Event` — event class;
- `X-Hub-Signature-256` — GitHub-style `sha256=<hex HMAC>` over the exact raw body.

Supported Phase 2 inbound event classes are deliberately narrow:

- `push` -> `CODE_UPDATE` review signal;
- `workflow_run` -> `CODE_UPDATE` verification/review signal;
- `issues` -> untrusted `DM` review signal;
- `issue_comment` -> untrusted `DM` review signal.

Unsupported event classes are rejected instead of being guessed into an action. A GitHub push does **not** trigger deployment. It is routed into the existing code-update review logic; without repeated verification evidence it remains escalated for owner review.

## Secret boundary

Adapter configuration stores only a symbolic reference such as `RUNTIME_GITHUB`. The runtime key is resolved from an injected environment variable named:

`AETHERIS_CONNECTOR_SECRET_<REFERENCE>`

For example, reference `RUNTIME_GITHUB` resolves from `AETHERIS_CONNECTOR_SECRET_RUNTIME_GITHUB`.

Raw HMAC keys are not written to connector tables, adapter API responses, evidence JSON, or Git history. The hosted proof creates ephemeral keys at runtime and passes them to the orchestrator container through Docker Compose interpolation.

Adapter API views intentionally expose only whether a secret reference is configured; they do not return the reference itself.

## Verification behavior

- HMAC-SHA256 comparisons use constant-time `MessageDigest.isEqual`.
- Malformed or incorrect signatures are rejected before Phase 1 connector ingestion.
- Generic webhook timestamps are freshness checked before ingestion.
- Accepted deliveries enter Phase 1 with `deliveryVerified=true`.
- Failed signature/freshness checks create no connector receipt and no executive-agent run.
- Replay of an already accepted delivery reuses Phase 1's unique `(connectionId, externalEventId)` receipt and does not execute the event twice.
- Request bodies are capped at 1 MiB.

## Configuration API

- `PUT /api/orchestrator/connectors/connections/{id}/adapter` — configure adapter type, secret reference and freshness window.
- `GET /api/orchestrator/connectors/connections/{id}/adapter` — inspect non-secret adapter status.

Provider/adapter compatibility is enforced:

- `GENERIC_HMAC_SHA256` requires a `GENERIC_WEBHOOK` connector;
- `GITHUB_HMAC_SHA256` requires a `GITHUB` connector.

Phase 1 status and capability gates still execute after cryptographic verification.

## Safety boundary

Phase 2 is inbound-only. It does not add `SEND_MESSAGES`, GitHub write operations, deployment mutation, billing execution, or other provider-side actions. Existing billing/deployment approval gates remain unchanged.

The hosted runtime proof uses synthetic webhook requests and ephemeral CI keys against the real orchestrator. It proves the HMAC algorithm, freshness policy, provider mapping, replay boundary and executive routing in the hosted environment.

It does **not** prove:

- a live GitHub App installation or live GitHub webhook configuration;
- Gmail/Outlook/Slack/Discord/WhatsApp/Instagram OAuth sessions;
- public internet ingress, WAF, TLS termination or DDoS controls;
- a production secret manager such as Vault/AWS Secrets Manager/Azure Key Vault;
- outbound provider mutation or external sending;
- provider SLA/capacity guarantees;
- physical-PC validation.

Physical-PC validation remains `BLOCKED_PENDING_HARDWARE`.

## Next connector pass

After Phase 2 is fully validated, the next logical pass is credentialed OAuth/read adapters—starting with Gmail/Outlook or another owner-selected provider—using least-privilege scopes and an external secrets/token-storage boundary. Outbound sending should remain draft/approval gated until separately implemented and proven.
