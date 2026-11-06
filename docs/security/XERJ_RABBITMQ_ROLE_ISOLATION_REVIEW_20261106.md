# Aetheris XERJ — RabbitMQ role-separated broker identities (disposable preview)

**Parent draft #387:** `637f11bffa444c6645bcab2a0f214e7ecdfbd019` (103 targeted Java tests, 84 live security checks passed in run 42178844034). This child review touches ONLY isolated preview configuration/test harnesses and documentation. It DOES NOT edit protected main, actual deployment/volumes/credentials, the production Compose configuration, or the existing 40-file XERJ candidate.

## Security boundary
A new hosted-only provisioner on a **fresh uniquely named RabbitMQ container** creates virtual host `xerj_review`, three distinct principals, and per-resource regex permissions:

- `xerj_ci` — ephemeral ADMIN **for topology bootstrap only**. The bootstrap JVM declares the durable `aetheris.events` topic exchange, `aetheris.audit.user-events` queue and exact `user.*` binding, then exits before service JVMs start.
- `xerj_publisher` — can **write** only to `aetheris.events`; denied `configure` for exchanges/queues, **read** on queues, and access to the root vhost.
- `xerj_consumer` — can **read** only `aetheris.audit.user-events`; denied `configure`, **write** on the exchange, and access to the root vhost.

All three passwords are unique 256-bit random hex strings, masked by GitHub Actions and generated for **each isolated run**. User's application JVM receives **only its own publisher credential**, Audit JVM **only its own consumer credential**; Identity and Gateway receive none of these broker credentials. The User and Audit Spring profiles opt out of automatic RabbitAdmin declaration when dedicated role mode is selected. The original default legacy shared-login settings are deliberately unchanged, so this is **not proof of production hardening**.

## Evidence gates
1. Shell and rendered configuration guard. New Java JUnit integration test authenticates to the REAL RabbitMQ AMQP protocol, declares topology via ephemeral admin, publishes and confirms a durable synthetic event using the publisher, and fetches it through the consumer.
2. Separate negative protocol calls require publisher queue read denied, consumer exchange publish denied, topology changes denied for both accounts and authentication to the root vhost denied. A real server channel/connection authorization error is required, not text matching alone.
3. The existing independently verified four-JVM hosted suite runs on the **same restricted broker**, with all previous real RS256/mTLS/User tenant UUIDs, PostgreSQL, Redis, audit-event attribution, forged-header and poison-message regressions. No synthetic AMQP messages remain on the queue before application startup.
4. Only fail-closed check names and booleans appear in the sanitized report; synthetic credentials, event bodies, tokens and AMQP traces are not uploaded. `docker compose down -v` destroys the single isolated CI project.

## Unresolved NO_GO risks
- This proves synthetic RabbitMQ **username/vhost ACL isolation**, not cryptographically signed event publisher identity. A compromised authorized producer password could still forge `tenantId`. Audit `tenantId` remains metadata, NOT an authorization source or legally authenticated audit evidence. Future production integration must use secrets manager/rotation, AMQPS with cert-verified workload identities, broker network policy and permissions across all other connected applications.
- No controlled dead-letter lifecycle on the existing durable queue. Its queue arguments must NOT be redefined in place without migration. Need a dedicated DLX/queue, restricted operators and alerting.
- SQL transaction and RabbitMQ publish are not atomic: durable transactional outbox with broker delivery confirms, retries and deterministic deduplication is outstanding.
- Multi-member tenant organizations, migration/restore/rollback, independent database roles, Redis AUTH, JWT revocation/rotation, real cluster mTLS, images and supply chain attestations, historic credentials and XERJ licences/consent remain unresolved.

**Stage 45 NO_GO / XERJ #376 HOLD / PRs remain draft.** No protected main merge, real-user changes or XERJ publication.
