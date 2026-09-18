# Connector Approved Actions — Phase 6

Phase 6 adds an owner-approved outbound action pipeline for Gmail, Google Calendar and GitHub without claiming live provider mutation.

## Supported action intents

- `GMAIL_SEND_EMAIL`
- `CALENDAR_CREATE_EVENT`
- `GITHUB_CREATE_ISSUE`

Each request is bound to an enabled connector connection, receives a unique idempotency key, creates an Aetheris task, moves that task into the existing owner-approval workflow, and cannot execute before its approval is granted.

## Runtime API

Base path: `/api/orchestrator/connectors/actions`

- `GET /policy` — returns the write-policy and truth boundary.
- `POST /` — create a connector action request.
- `GET /` — list recent persisted action receipts.
- `GET /{id}` — inspect one action and its task/approval linkage.
- `POST /{id}/execute` — execute only after approval.

The request fields are `connectionId`, `actionKind`, `idempotencyKey`, `targetRef`, `summary`, and optional `executionMode` (`SYNTHETIC` by default or `LIVE`).

## Safety model

Outbound connector writes are action-scoped rather than silently added to the existing read-oriented connector capabilities. Every consequential action is represented by an Aetheris task plus a high-risk owner approval. An unapproved execution returns a conflict and performs no mutation.

Synthetic execution is deterministic and writes only a persisted `synthetic://...` receipt. Reusing the same `(connectionId, idempotencyKey)` returns the original action, and re-executing a completed action returns the original receipt with `replay=true`.

Live writes are disabled by default through `aetheris.connectors.live-writes-enabled=false`. Even if that policy gate is explicitly opened, this Phase 6 implementation still fails closed because live provider mutation adapters are intentionally not installed in the hosted proof. Real Gmail sends, Calendar event creation, and GitHub issue creation require a later separately credentialed, explicitly enabled live-validation lane.

## Hosted runtime proof

`.github/workflows/connector-approved-actions-runtime-proof.yml` starts the real orchestrator container and runs `tools/connector_approved_actions_runtime_smoke.py` against the HTTP API. The proof verifies health, policy defaults, approval creation and visibility, unapproved blocking, approved synthetic execution for all three providers, request/execution idempotency, persisted receipts, audit linkage, live-write fail-closed behavior, and the truth boundary.

Evidence contract: `build-evidence/runtime/connector-approved-actions-phase6-contract.json`.

Evidence class: `HOSTED_RUNTIME_SYNTHETIC_WRITE`.

## Truth boundary

This phase proves owner-approval orchestration, persisted idempotent action receipts and synthetic Gmail/Calendar/GitHub write adapters in hosted Docker runtime. It does **not** prove live provider mutation, production autonomy, production authorization policy, provider-side delivery semantics, or physical-PC behavior. Physical-PC validation remains `BLOCKED_PENDING_HARDWARE`.
