# Aetheris Audit — local transaction-scoped membership fence (NO_GO)

**Stacked base:** verified draft [PR #402](https://github.com/teldigi5-wq/aetheris-platform/pull/402), commit `e3e2687fb0f3827dba1e9bdc7db4fe915d071587`. This branch changes real application code, not only the test harness. **Production promotion is explicitly blocked.**

## Threat / gap

The opt-in RS256 origin proof in PR #400 verifies the private-key holder and the exact tenant UUID that the holder signed. PRs #401–402 proved that a running Spring Audit `@RabbitListener` verifies and persists signed events under a restricted RabbitMQ identity. None of these ensure that the **tenant's current membership authority** permits a particular `userId` at the instant the event writes to Audit. A queued, once-valid signed event could outlive authorization revocation. A pre-write check against an independent service could race a revocation after the check but before the local transaction commits.

## The bounded actual fix

New additive, **V3 Flyway migration** `audit-service/src/main/resources/db/migration/V3__audit_local_membership_fence.sql` creates `audit_user_tenant_membership_fence` with an explicit `(tenant_id,user_id)` primary key, uppercase `ACTIVE/REVOKED` status, `valid_since` timestamp and a required positive revision. **No membership is inferred or inserted from broker messages**, and there is **no implicit ACTIVE default**. Incoming events cannot update the membership table through application code or a new API.

New actual Spring bean `AuditLocalMembershipFence` is independently opt-in via `AETHERIS_AUDIT_LOCAL_MEMBERSHIP_FENCE_REQUIRED`, which remains **false** by default. Its `SELECT ... FOR SHARE` requires exactly one existing ACTIVE row for the exact tenant UUID and user ID. It rejects missing, revoked, tenantless and pre-`valid_since` events with a sanitized, permanent non-requeue `AmqpRejectAndDontRequeueException`. Other SQL failures propagate, remaining retryable rather than misclassified as invalid events.

`AuditEventStore.consume` invokes this fence **after existing RS256 signature validation and before the existing immutable-event-ID repository insert**. Both the SELECT row lock and the Audit insert participate in the same Spring `@Transactional` method. Consequently, updates to the same local membership row must wait for the Audit event's commit if this event linearizes first, and a committed revocation before admission causes denial. This addresses that **specific local PostgreSQL check/write TOCTOU**; it does not guarantee globally atomic, cross-cluster or independent authorization decisions.

The injectable production `AuditEventStore` constructor rejects startup if this membership gate is enabled while RS256 event-origin verification is disabled. Existing test and legacy constructors and the default-disabled real runtime remain compatible.

## Exact CI acceptance

- `AuditLocalMembershipFenceIT` must run on the same disposable PostgreSQL16 Audit database used by previously verified restoration tests, after the synthetic restoration. The CI applies V3 **twice** there to test additive idempotence; neither production nor the user's own PC is altered.
- Cases: no membership row rejects a syntactically and cryptographically valid event; ACTIVE admits an exact tenant/user event; REVOKED rejects without an Audit row; re-authorized ACTIVE does **not** revive signed events occurring before the latest `valid_since`; wrong user and tenantless events deny; and fail-closed startup forbids unsigned origin alongside required membership.
- Concurrency test: one genuine JDBC transaction executes the same local membership `SELECT FOR SHARE` and Audit insert, holds the transaction open, while a second attempts a revocation UPDATE. Only **PostgreSQL SQLSTATE 55P03** lock timeout satisfies the race proof, not arbitrary JDBC exceptions. After the initial commit and separate revocation commit, a new validly signed event is rejected.
- The exact-head CI must re-run the prior **121 Java security tests**, real Spring listener RSA/DLQ under restricted Rabbit consumer, outbox, broker custody, signed replay, dual database restore/witness and **90/90** historical integration assertions; this does not mean all test types beyond the explicitly listed were independently revalidated.

## Scope and remaining exposure

**This is a local authorization mirror, NOT an authoritative tenant-membership service.** The branch does **not** implement or authenticate a feed from the Identity/User authority, least-privilege/mTLS secured membership replication, durable quorum read, monotonic independent recovery epoch, rollback resistance, authority-generation binding in event signatures, or safe synchronization after database restore. A privileged local database operator can still roll the mirror back or mutate a row; this is not independently trusted or tamper-evident. No production reviewer has proved real membership provisioning or event provenance. The `valid_since` comparison relies on the signed producer's time claim, which a compromised signing key can falsify. Reconciliation freshness must be backed by an external, authorized source with an independently verifiable watermark **before enabling the feature in production**. The status flag alone is not a substitute for real authorization.

**Rollout considerations:** Turning this on without a complete, trustworthy and current local membership snapshot will deliberately quarantine *all* unknown or legacy tenantless User events, including potentially legitimate historical messages. The operator must first stop/drain broker ingestion under a governed protocol, inventory queued events, verify actual tenant/user mapping, plan quarantine disposition, give the consumer a SELECT-only DB role, provide a privileged authenticated writer/sync role separate from the consumer, and approve a restore/recovery reconciliation procedure. Never use User events to self-grant authority.

Aetheris Stage 45 remains **NO_GO**, Platform #251 and Runtime #23 OPEN, separate-witness S-B62-A still not an independently irreversible authority and nine historical release attestations outstanding. The separate XERJ source contribution track remains **2/6 complete, four publication stages left** (human privacy/history, licence, valid reviewed manifest and owner/maintainer signoff), with synthetic JWT historical exact-byte reuse findings in PR #361 still under investigation. This code must not be misconstrued as closing those gates.

**Operational decision:** leave feature OFF by default; draft/unmerged; do not migrate a production database, distribute real keys, provision production authorities, merge, deploy, force push or publish XERJ source without separately recorded owner approval.

**Evidence status:** Only report this exact branch as tested after completed exact-head GitHub Actions is inspected.
