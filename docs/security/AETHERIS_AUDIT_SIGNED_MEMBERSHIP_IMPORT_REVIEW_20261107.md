# Aetheris Audit — separately signed local membership revision importer

**Review-only implementation. Release and XERJ source publication: HOLD.**

**Parent:** CI-verified draft PR #403, `633ede3a558054430c920cd36e2670c62b743264`. This is an opt-in real-service component with no network ingress or automatic membership provisioning. It is NOT an authoritative tenant-membership reconciler, and neither user- nor operator-controlled input is accepted as a membership grant without a separately pinned RS256 verifier.

## Existing model and why this is narrowly scoped

The current Identity and User draft migrations `20261106_01_identity_account_tenant.sql` and `20261106_02_user_record_tenant.sql` add nullable `tenant_id` fields, but define no independently authenticated member/grant/revocation event stream or durable authoritative membership generation. PR #403's local `audit_user_tenant_membership_fence` blocked missing/revoked rows in the same local database transaction as the Audit insert. However, an unrestricted writer could rewrite its `ACTIVE` rows or reuse an older local revision. No User domain event may self-assert membership.

## New bounded signed update acceptance

The actual `AuditSignedMembershipImporter` Spring component is **OFF by default**, controlled by `AETHERIS_AUDIT_MEMBERSHIP_IMPORT_ENABLED=false`. Enabling requires an operator-supplied **pinned, RSA-3072+ X.509 public key** and independent `AETHERIS_AUDIT_MEMBERSHIP_AUTHORITY_KEY_ID`, distinct from User event-origin KID. There is **no default key, private key in Audit, HTTP endpoint, queue subscription, implicit live approval or identity self-provisioning**. A private authority signer exists only as an ephemeral RSA keypair in synthetic integration tests, never as a production key.

A JJWT RS256 proof must have exact `iss=aetheris-membership-authority`, `aud=aetheris-audit-membership-reconciliation`, `sub=tenant-user-membership`, matching `kid`, canonical `jti` and `tenantId` UUIDs, positive integral `userId` and `revision`, explicit `ACTIVE` or `REVOKED`, canonical UTC `validSince`, issued/expiration time and **no unknown JWT claims**. The signer authorizes a *specific change*, not an Audit event's own asserted tenant. Invalid, forged, expired, future-issued, wrong-key, wrong-audience, wrong-issuer or extra-claim proofs produce a sanitized rejection; raw tokens, user IDs and personal data are never emitted.

The actual update uses one PostgreSQL `INSERT .. ON CONFLICT .. DO UPDATE .. WHERE .. RETURNING` statement: accept first authorized row or a strictly **higher revision** for the same `(tenant_id,user_id)` with nondecreasing `valid_since`; after REVOKED, only accept ACTIVE with **strictly later** `valid_since`. Conflicting same-revision updates, older revisions, same-time reactivation and backdated reactivation are refused. The row lock and compare-and-swap occur atomically inside the same PostgreSQL statement; this addresses stale concurrent local writes. Duplicate signed updates are deliberately rejected rather than treated as authorization; any eventual real transport must document ACK-gap and redrive behavior.

The existing `AuditLocalMembershipFence` reads the resulting rows but remains independently **OFF** by default and requires signed event-origin verification if enabled. Importing a grant **does not** authorize a production consumer until separately approved, and a user event cannot call this importer. JDBC outages still propagate as retryable errors, not permanent signature denials.

## Reproducible CI acceptance

The new test `AuditSignedMembershipImporterIT` requires the exact disposable GitHub-hosted runner and the restored, synthetic `xerj_fence_audit` PostgreSQL16 database. It uses fresh RSA-3072 authority keys, the previously verified additive V3 table, and only random synthetic tenant/user IDs; all test-created rows are removed. It must prove:

- ACTIVE revision 1; REVOKED revision 2; ACTIVE revision 3 only after a later `validSince`; old authorized event-time denied by PR #403's local membership fence.
- Conflicting same revision and older revisions refused without replacing the existing row; concurrent signed revisions 2 and 3 never leave an earlier final local revision, regardless of execution order.
- Wrong private signing key, wrong KID, issuer, audience, expiry, extra claim, malformed token and weak/disabled/misconfigured public verifier cannot create a membership row.
- The signed update path propagates transient database faults for retry. No seeded identity production data, network reachability to real Identity/User services, or operator key are required.

Exact-head CI must also run the prior **121 Java security regressions**, local membership-lock IT 3/3, real automatic signed-mode Spring Audit Rabbit listener, three-database restore, whole-vhost custody, signed replay, synthetic separate witness, and existing **90 real disposable RSA/mTLS/outbox/tenant checks**.

## Critical remaining blockers — no production trust claim

A *valid signature* is only trustworthy if its **private-key holder** is independently verified to be the actual, authorized current membership authority. This change does **not** implement that authority, authoritative source reading, authenticated sync transport, signed membership snapshot/full-state reconciliation, bounded staleness or missing-revocation detection. A source that never emits a revocation cannot be fixed by increasing local revision numbers. PostgreSQL revision ordering is **not external anti-rollback**: a privileged backup restore could roll back the entire mirror, including its last seen revision. The separate synthetic witness in PR #398 remains controlled by the same CI operator, and authority/witness governance is unresolved. The known User event private-key holder may fabricate `occurredAt`; event signatures do not establish real historical membership.

Before any operator enablement, require a genuinely authorized membership authority; scoped issuance privileges and signing-key custody independent of User event-origin keys; mTLS/identity binding for transport; monotonically durable external generation and anti-rollback witness; freshness/watermarks and complete-snapshot/quarantine plan; read-only Audit DB membership privileges separate from write-only reconciling principals; revocation recovery after partitions and old-primary resume; emergency key rotation; DLQ disposition; and a signed owner acceptance record. No equivalent controls have been proven here.

Aetheris Platform security #251 and Runtime #23 remain OPEN; S-B62-A old-primary/epoch rollback proof is NOT closed; Stage45 remains **NO_GO** with nine historically missing independent attestations. XERJ contributor issue #360 is a separate publication track: **2/6 formal stages completed, four remain**, with the previously identified synthetic JWT fixture appearing in ten other reachable historical test blobs and human privacy/history, licence, consent and maintainer approval still blocked.

**Decision:** draft, unmerged, no production migration, no production signing/verification/sync enablement, no real key distribution, no main branch change, no source-corpus repin or XERJ submission. Only label this branch CI-verified after the updated exact SHA passes.
