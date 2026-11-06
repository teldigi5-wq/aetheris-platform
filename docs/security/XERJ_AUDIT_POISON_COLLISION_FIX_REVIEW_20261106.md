# Audit event ingestion — actual listener poison/collision hardening

**Stacked parent:** verified Aetheris Platform draft PR #398 at `1ac6523feb975592922c910b38b7ae3d13eea467`. Child is a review-only **real application code fix**, with tests. This change must not be confused with the XERJ six-stage source-publication roadmap or with a production release.

## Actual service changes

### Audit listener
`audit-service/src/main/java/io/aetheris/audit/AuditEventStore.java` now validates *the entire existing User domain event envelope*, not just optional `tenantId`, before invoking the durable repository. It requires exactly six existing keys (or seven with tenant attribution), rejects unsupported or null keys, missing fields, wrong basic types, unsupported event types, nonpositive/nonintegral User IDs, invalid ISO-8601 occurrence times, control-character fields, and oversized identifiers/text. Only `user.created`/`user.deleted` and previously supported legacy `USER_CREATED`/`USER_DELETED` remain accepted. Legacy tenantless global events retain `null` tenant rather than falsely deriving ownership.

A permanent malformed payload throws `AmqpRejectAndDontRequeueException` with a **static sanitized message**, and makes no repository call. The exception handler is deliberately limited to validation. Actual `DataAccessException` and transient storage failures are **not swallowed or reclassified as poison**; they still propagate to the broker for retry. When a properly configured broker DLX is in place, rejected events are eligible for restricted operator quarantine. Application code cannot independently guarantee that production broker routing is configured correctly.

### Existing event ID collision
`audit-service/src/main/java/io/aetheris/audit/AuditEventRepository.java` retains the idempotent `INSERT ... ON CONFLICT (event_id) DO NOTHING` used for real RabbitMQ at-least-once replay. On an actual conflict, it now loads the existing immutable event and compares event type, user ID, name, email, **tenant UUID**, and event timestamp (allowing at most 1 microsecond of PostgreSQL/Java timestamp precision discrepancy). A replay matching the prior event is accepted without adding another record. A different payload reusing the same event ID fails with sanitized non-requeue rejection and **does not mutate or reassign the original Audit row**. Unavailable/ambiguous database reads fail as retriable storage/invariant errors, not as a silently accepted duplicate.

### Source tests
`AuditPoisonEnvelopeTest` uses service-layer mocks to verify invalid/missing/out-of-range, unsupported type, malformed tenant, blank/control/oversized fields and unknown keys are rejected without writes, while legacy/canonical payloads still persist. It asserts that a synthetic infrastructure outage remains a retriable `DataAccessResourceFailureException`.

`AuditImmutableEventCollisionIT` uses **real isolated PostgreSQL16** on the exact hosted runner project and synthetic `xerj_fence_audit` database. It checks identical duplicate idempotency, cross-tenant and missing-tenant collisions, type/user/name/email/time discrepancies, preserved existing row, and the legacy tenantless case.

The child GitHub Actions workflow retains the exact stack's historical four-service security regressions (JWT/RSA/mTLS, tenant, outbox and audit), PostgreSQL migration and backups, broker ACL/DLQ/outage/ACK-gap, three-DB restore, queue custody, RS256 replay, and second Postgres witness checks. Only run **success at the child exact SHA** can certify CI for these changes.

## Important remaining gaps (NO_GO)

- **This does not authenticate broker message origin or tenant ownership.** A sufficiently privileged broker publisher can still fabricate a valid-shaped *new* event ID. The optional tenant in the payload is not a signed ownership proof, and real replay-time membership validation is not implemented by this change.
- Real producer and consumer generation fencing is still not wired up. The separate witness in PR #398 is a disposable reference system, not a rollback-resistant quorum; old primary fencing and cross-cluster TOCTOU remain unresolved (S-B62-A).
- A malformed payload may fail earlier in the converter/broker route, outside the listener validation. The real production DLX, message size limits, broker access controls and quarantine disposition still require independent configuration/testing/approval.
- This tightens the previously loose acceptance envelope. Unknown future fields, non-positive user IDs and malformed historical messages now fail closed. Rollout requires inspecting actual queues and legacy schema/event shapes rather than enabling blindly.
- A new authentic event may still fail under broker/DB outages; no exactly-once guarantee. There is no claim of full vulnerability remediation, production release, runtime owner/SSE authorization, or formal security certification.
- No files from XERJ's proposed pinned 44-file corpus were repinned or submitted. XERJ issue #360 remains **2/6 complete, four formal publication stages outstanding**, requiring human privacy/history/licence checks, validated manifest and owner approval. Aetheris Stage45 is **NO_GO** with nine recorded outstanding operator attestations, Platform #251 OPEN and Runtime #23 OPEN.

**Status:** additive review change on a child draft PR only, no protected-main update, merge, deployment, real broker purge, production database migration, secret change or XERJ submission. Production promotion must wait for exact-head security CI, compatibility review and a human release decision.
