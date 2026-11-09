# Aetheris review-vhost RabbitMQ recovery custody — fail-closed experiment

**Lineage:** verified [PR #395](https://github.com/teldigi5-wq/aetheris-platform/pull/395) head `a3928b9d203565e7bb8c93c9c4933240ddaef928`, child of PR #394. New branch is a **draft review candidate**. It does not revise the original XERJ six-stage publication roadmap or Stage 45 release criteria.

## Exact scope

The previous disposable test proved a restored Identity/User/Audit snapshot cannot be promoted safely while **one known RabbitMQ queue** has unacknowledged or requeued messages. The next necessary negative proof is to establish that a healthy source queue alone is insufficient: a poison/DLQ, an unexpected queue, or a still-active consumer can preserve a delivery hazard even when the preceding observation looked clean.

The `BrokerVhostRecoveryCustodyIT` integration test runs **only** on the unique hosted GitHub Actions Docker Compose project `xerj-mtls-ci-GITHUB_RUN_ID-GITHUB_RUN_ATTEMPT`, after the existing three-database restore script and `BrokerRestoreInflightIT`.

The test creates one random and one unexpected short-lived disposable queue using the ephemeral CI broker administrator. It enumerates **every queue within `xerj_review` vhost** using the actual RabbitMQ 4 `rabbitmqctl list_queues` output, counting messages ready, messages unacknowledged, and registered consumers. Inventory failures and unknown formatting deny review admission. An exact allowlist is required; an **unknown but empty** queue also denies the result. The `aetheris.audit.poison` operator-only queue is included even when ordinary consumers cannot read it.

## Executed scenarios — MUST pass at the new exact head before claiming success

1. The previous controlled PostgreSQL16 recovery fixture has matching Identity, User and Audit `xerj_dr_fence` UUIDs at fence B and stable expected user/tenant/Audit data. The two original broker queues are empty and have no active consumers.
2. Create an extra declared queue with known review-only name and show that a matching **complete** vhost inventory admits the **limited synthetic review check**, not production promotion.
3. Register an actual RabbitMQ manual-ack consumer on an empty review queue. While `consumers=1`, deny the review gate; after cancellation and a fresh inventory, allow the limited check.
4. Publish a persistent synthetic message into the operator-only poison queue on **disposable** RabbitMQ. While `messages_ready=1` or `messages_unacknowledged=1`, deny, then acknowledge the exact synthetic message and check that all expected queues are idle.
5. Create a fresh queue which is **not** on the reviewed inventory allowlist. Even if empty, require denial until it is deleted (on the disposable broker only).
6. Capture an initially clean inventory, then publish a late message to an expected queue. Re-evaluate **fresh inventory**, deny until ready/unacked counts are zero. A cached observation is not a cutover grant.
7. Regress only the disposable Audit database fence marker to the old generation marker A. Require denial despite empty queues; restore the original fixture marker in a `finally` block. This is a **mutable synthetic marker**, NOT independently witnessed or non-rollbackable fencing.
8. Delete only test-owned queues; rely on inherited CI cleanup to destroy the unique test Compose project and temporary keys. Preserve all previous Java, mTLS/RS256, tenant, outbox, ACK-gap, broker identity, DLQ and recovery checks.

The review probe never enumerates or changes live infrastructure, application broker ACLs, production credentials, real backups, production code, migrations, or deployment settings. A failed/busy broker cannot pass the predicate.

## What passing this DOES NOT establish

- This inventories **one disposable RabbitMQ vhost**, not all production vhosts, quorum/stream queues, exchange bindings, retry plugins, delayed/dead-letter routing, multiple brokers, replicas or consumers disconnected from the broker.
- Queue and consumer counters are not atomic with multi-database transactions or a producer freeze. A publisher may insert after the last read, and even repeat reads cannot certify an independently enforced cutover.
- The review token `xerj_dr_fence` is an in-database UUID: it is **not a signed capability, independently monotonic authority or enforced write epoch**. The test proves old marker mismatches are rejected, not that a privileged coordinator cannot reset history.
- Current Audit consumption validates canonical tenant UUID shape but not **current authorized tenant membership, resource owner, cryptographic producer proof, or recovery-generation fencing** for stale replay.
- The test's ephemeral broker administrator is **not** a production workload model. Existing CI checks separate publisher and consumer broker ACLs, but that is not a complete authenticated cutover protocol.

**Next engineering gate:** authenticated producer/consumer quiescence and generation-aware replay admission, independently enforced against old owners; broker-wide inventory, complete outbox/inbox reconciliation and actual multi-resource recovery with human approval.

**Status must remain:** Platform issue #251 OPEN, Runtime issue #23 OPEN, S-B62-A independent witness BLOCKED, Stage 45 **NO_GO** and XERJ issue #360 **HOLD** (formal publication stages 3–6 incomplete). Do not merge, deploy, publish, or repin XERJ from this test.
