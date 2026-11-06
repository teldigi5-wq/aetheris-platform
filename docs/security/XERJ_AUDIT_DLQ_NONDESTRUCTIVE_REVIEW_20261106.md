# Aetheris Audit poison-event dead-letter review — isolated hosted CI only

**Stack parent:** #388 at `3a8bc037b90a856f6ebaa5da90757528958b6300`. This child does NOT change protected main, production broker/vhosts, durable queue declarations, real credentials/volumes, any deployed service, or upstream XERJ #376.

## Precise change
The existing durable `aetheris.audit.user-events` queue is declared exactly as before with `new Queue(USER_AUDIT_QUEUE, true)`; no `x-dead-letter-*` arguments are introduced into its declaration, and no queue is deleted/re-created. A **disposable CI topology admin** declares a NEW durable `aetheris.audit.poison` queue, a separate `aetheris.audit.dlx` direct exchange and a `quarantine` binding inside the unique `xerj_review` vhost. A narrowly matched RabbitMQ operator policy, `xerj-ci-audit-poison`, attaches the dead-letter exchange and routing key to the source queue via `rabbitmqctl set_policy ... --apply-to queues`. This avoids hardcoded queue arguments and follows the official RabbitMQ policy guidance: https://www.rabbitmq.com/docs/dlx .

Only the synthetic topology administrator can read the dead-letter queue. The live Audit consumer can read only the original source queue, not the poison queue; the User publisher cannot read queues or configure exchanges. No automatic retry loop or automatic DLQ replay exists in this preview.

## Verified gate required before release claims
1. Real broker ACL and role segregation passed first; applying policy is limited to the newly created CI vhost and named source queue. The Java bootstrap safely declares the existing durable queue with **no optional arguments** even when the policy applies, then checks quarantine is initially empty.
2. All **84** existing real Gateway→Tomcat RSA/mTLS, tenant owner, Redis/Postgres, two-account cross-tenant denial and valid Audit delivery checks must still pass.
3. While the actual Spring Audit AMQP consumer is running, a restricted publisher sends **one valid-format but semantically poison tenant-ID JSON event** (synthetic account and event ID). The listener must reject it with `AmqpRejectAndDontRequeueException`; real RabbitMQ then routes it to the operator-only DLQ.
4. The test checks the real dead-letter envelope exchange/routing, first-death reason `rejected`, source queue identity, precisely one quarantine record and **zero Postgres audit rows** with that poison event ID. The publisher and consumer must be unable to read quarantine.
5. Private credentials, raw message bodies, access tokens, TLS keys and real application logs must never be uploaded. CI tears down ONLY its uniquely named disposable Compose project and volumes.

**No existing user records or live queues are modified.**

## Remaining security/reliability blockers
- This is an at-most-once RabbitMQ classic queue dead-letter path in a test lab, **not lossless guaranteed poison custody**. If the DLX or target queue is unavailable, messages can be lost. Production needs a reliability policy, queue-type/cluster design, monitoring, restricted operator access, retention, alerts, documented manual reprocessing and tested recovery.
- Non-tenant malformed event fields and schema/size validation need broader rejection consistency; this stage focuses on malformed tenant provenance.
- Shared production broker login remains; account isolation only proven in test environment. AMQPS client-auth, key rotation and network controls pending.
- RabbitMQ publish and PostgreSQL commit are not atomic; User service still publishes inside open JDBC transactions. Next stage is **transactional outbox with post-commit polling and idempotent confirmed delivery**, plus failure injection and replay evidence.
- Global ADMIN-only Audit read, no trusted cryptographic event provenance, no multi-member tenant organization model, no production schema/backup/rollback approval, no fully checked dependencies/Stage45 attestations, and no XERJ per-file license/owner publication consent.

**Aetheris Stage45 NO_GO · XERJ #376 HOLD.** No automatic merge, deployment, repin or publication permitted.
