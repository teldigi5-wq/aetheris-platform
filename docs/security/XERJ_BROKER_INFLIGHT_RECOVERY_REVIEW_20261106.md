# RabbitMQ in-flight vs multi-database restore — review-only interruption proof

**Parent:** Platform draft PR #394 at `579179dced1363679304247177a509d788eaf1b0`.
**Change class:** test + CI + documentation only, stacked on the verified existing review branch.
**Release:** Stage 45 **NO_GO**. **XERJ HOLD**. No protected-main change, production broker/database mutation, credential rotation, merge, deployment, or contribution.

## Why the previous milestone is not sufficient

The synthetic three-database fence in PR #394 matches restored Identity, User, and Audit logical database state, including stable outbox event IDs and tenant metadata. PostgreSQL backups do **not** contain RabbitMQ durable messages, deliveries currently unacknowledged by consumers, or requeues on connection loss. Therefore an internally consistent restored database set can race a still-active broker or an older in-flight event.

## Controlled experiment

`BrokerRestoreInflightIT` runs only in the uniquely named, disposable hosted CI project `xerj-mtls-ci-GITHUB_RUN_ID-GITHUB_RUN_ATTEMPT` created by the existing security workflow. It runs **after** `xerj_multidb_recovery_fence.py`, reusing only its synthetic named PostgreSQL logical databases. A fresh queue is declared in the disposable RabbitMQ `xerj_review` vhost, with a random name, persistent messages, and an expiry. The test fixture uses the ephemeral CI-only broker administrator; **application broker ACLs remain unchanged**.

Checks:
1. Verify Identity/User/Audit source fences and known event/tenant match. Deny the earlier Audit recovery snapshot and the deliberately forged-tenant Audit restore.
2. Initially accept the limited condition that the **single known isolated queue** is empty with coherent databases. This is **not** full-system promotion authority.
3. Publish a durable synthetic user-created event, confirm acceptance by RabbitMQ, consume it with manual ACK disabled, and inspect **actual broker** ready/unacknowledged counters through the disposable container's `rabbitmqctl`. Deny while unacknowledged even though all three database fences match.
4. Reattempt the same Audit insert with its existing real `ON CONFLICT(event_id) DO NOTHING` semantics: zero new rows and exactly one original Audit row.
5. Close the receiving channel *without* ACK (simulated consumer failure after commit). Observe RabbitMQ requeue of the same message, deny recovery while ready, then consume the redelivery, verify identical bytes and `redeliver` flag, and deny while the second delivery is unacknowledged.
6. ACK, observe the isolated queue drain, and verify that the known synthetic database set meets the *limited* local gate. Mixed and tenant-forged restored Audit copies must remain denied.
7. Publish a late durable copy **after** the empty-queue observation; observe that the decision is no longer valid until the newly ready message is handled. This explicitly demonstrates the TOCTOU risk.
8. Delete only this fresh synthetic queue and destroy the ephemeral Compose project through the unchanged parent workflow cleanup.

The workflow retains the parent Java security suite and real RSA/mTLS, tenant isolation, outbox, DLQ, broker identity, database backup/recovery, ACK-gap and 90-check live tests. A green PR check would prove only this specific host/commit/review fixture.

## Unresolved production release blockers — fail closed

- The test observes **one known synthetic queue**, not every production vhost, quorum/durable queue, DLQ, consumer or broker replica. It does not enumerate or attest to broker-level snapshots and message custody.
- Queue emptiness is **not** a write fence. A producer, delayed retry, disconnected old owner or privileged operator can publish *after* a successful observation. No atomic commit barrier links RabbitMQ and three PostgreSQL databases; this test cannot claim exactly-once delivery.
- A trusted recovery coordinator must authenticate, quiesce/fence producers **and** consumers, establish/attest a monotonically enforced recovery generation, obtain broker and database inventories, drain/quarantine or reconcile each event against durable outbox/inbox and current tenant authority, and recheck at cutover.
- The Audit consumer currently parses canonical `tenantId` but does **not itself establish current resource ownership or a signed recovery generation for a historical replay**. A stale but correctly formed event could still be accepted through a reachable broker path. This is an open security design gap, not evidence of a patched production control.
- The previous separate S-B62-A independent, non-rollbackable old-primary fencing requirement remains open. No real infrastructure anti-rollback attestation, snapshot/PITR coordination, external signoff, secret/history disposition or Stage 45 release matrix closure is established.
- XERJ issue #360 still requires human privacy/history and licence/attribution review, manifest validation and explicit owner authorization. Do **not** repin its corpus or submit upstream.

**Decision rule:** This negative experiment cannot turn Stage 45 `NO_GO` or XERJ `HOLD` into GO. CI must finish successfully at the exact new child PR head before its test is marked verified.
