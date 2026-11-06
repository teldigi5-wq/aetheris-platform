# Aetheris User transactional outbox — additive review only (NO_GO)

**Exact parent draft:** PR #389 at `9704f22f760ee4cbec15bf11f2aedeeb4662497b`; real RabbitMQ poison DLQ + RSA/mTLS + tenant/Audit checks passed. This child is an **isolated preview**, not a protected branch merge or production deployment.

## Failure being addressed

All existing User `create`, `delete`, `createForTenant` and `deleteForTenant` methods were issuing `RabbitTemplate.convertAndSend` inside an open JPA/Postgres transaction. This can publish a ghost event when the SQL transaction later fails, or commit a User record and lose the audit event when RabbitMQ fails.

## Bounded implementation

- Adds manual, **additive** PostgreSQL 16 `user_event_outbox` table with a stable primary-key `event_id`, original User identifiers/names/email, optional server-sourced tenant ID, state, retry attempts and expiring lease/token columns. Never auto-applied on production, no migration/backfill guessed from old events.
- `AETHERIS_OUTBOX_ENABLED=false` by default, preserving historical app wiring while migration and owner approval are outstanding. Opt-in mode requires the table already exist; startup **fails** if absent. Existing JPA `ddl-auto=validate` is not disabled.
- In opt-in mode `UserEventPublisher` inserts into the outbox using `JdbcTemplate` and **requires an active writable Spring transaction**, instead of calling RabbitMQ directly. The same Spring-managed DataSource transaction rolls back User + outbox together.
- A scheduler uses a bounded single-row PostgreSQL `UPDATE ... FOR UPDATE SKIP LOCKED` atomic claim with 30-second lease and unique lease token; no DB transaction crosses the outbound network request. PostgreSQL keeps a stable event ID through expired claims/retries. Audit `ON CONFLICT(event_id) DO NOTHING` is still the independent dedup boundary.
- The relay requests RabbitMQ **correlated publisher confirms** and mandatory-return detection before marking a leased record delivered. Failed, timed-out, unroutable or NACKed messages are retained for exponential retry (capped delay 300 seconds), with at most 12 attempts before parking for **explicit operator investigation**. No automatic discard or silent direct-fallback.
- Audit delivery remains **at-least-once**. A crash after broker confirm but before Postgres delivery mark can republish the same `event_id`; duplicate *delivery* can happen, but the verified Audit database has idempotent event IDs. A stable ID is not end-to-end exactly-once and does not guarantee message ordering.

## Hosted proof required
1. Unit tests prove opt-in publisher NEVER calls direct RabbitMQ, errors do not fall back, stable IDs are reused, and only ACKs mark delivered.
2. Actual disposable Postgres integration uses `TransactionTemplate` to force an outbox insert rollback and verify zero rows, then commits a row, forces an expired lease/reclaim, denies stale ACK and verifies the same eventId is delivered.
3. New hosted live test repeats all **84** previously passing security checks with outbox mode enabled across actual Identity/Gateway/User/Audit JVMs, real RSA/mTLS, separate Rabbit publisher/Audit consumer, tenant DB and operator DLQ. It then **stops the disposable RabbitMQ broker**, commits a real HTTP User mutation (201) and verifies its outbox row exists unconfirmed; after restarting the same broker, it waits for the relay to publish and Audit to persist **exactly one row** matching the stable event ID. No secret/payload/artifact logging.
4. The original existing durable source queue and newly attached policy remain unchanged. Disposable container volumes destroyed after proof; no production AMQP/DB systems are touched.

## Remaining operational/security blockers
- Production SQL migration/backup/restore/rollback and outbox state/operator runbook have not been performed; queue lag, parked records and retry alerting require dashboards and SLOs. Outbox contains PII (name/email) and needs reviewed retention, access control, backup encryption and erasure handling.
- RabbitMQ confirmation only proves broker accepted a routed message. It does not guarantee durable replication under all broker crashes, eventual consumer success, or independently authenticated producer identity. Production broker credentials, mTLS/AMQPS, network ACL, queue quorum reliability and operator-only quarantine are not proven.
- The proof replays an artificial broker outage; full crash matrix needs worker termination, DB rollback under concurrent writes, multiple relay instances, process death *after ACK before state update*, and safe replay/park/recovery tests across restarts and PostgreSQL lock stress.
- Existing tenant ownership is account-bounded, not organization membership; production SQL and audit-scoped policy, global uniqueness, identity JWT revocation/PKI, Redis AUTH, least-privilege DB and independent Stage45 attestations remain unresolved.
- XERJ #376 historical corpus/source pin is unchanged. Per-file licences, privacy and explicit publication owner consent remain BLOCKED.

**Aetheris Stage45 NO_GO · XERJ HOLD**. Do not merge, deploy, repin or publish this preview.
