# Synthetic tenant membership lifecycle — security review candidate

**Date:** 31 October 2026  
**Parent:** [draft PR #358](https://github.com/teldigi5-wq/aetheris-platform/pull/358), RS256/mTLS backend API boundary  
**Decision:** NO_GO for production. This is a disposable PostgreSQL/JUnit rehearsal, **not** a deployed tenant-authorization implementation.

## Verified prior boundary

The real Identity issuer has no authoritative tenant membership in its signed tokens. Identity and User records use unrelated sequences and have no approved durable ownership schema. The real User backend currently denies API_CONSUMER object lookup, while synthetic CI proves only explicit Identity-to-User object bindings. No customer permissions are enabled by this candidate.

## Threat addressed

A signed token and an exact object-binding record are insufficient if an account has since been **revoked from the tenant** or its membership has **expired**. A future authorization check must require, in the same read, (1) a verified subject; (2) an exact authoritative tenant membership; (3) that membership being active, unrevoked and unexpired; and (4) an independently verified user-object binding to that tenant.

## This review-only change

The **existing disposable PostgreSQL 16 script** now creates an additional `user_stage.synthetic_identity_tenant_memberships` fixture with composite `(identity_uid, tenant_id)` key, `expires_at` and `revoked_at`. It belongs to the synthetic migration role, while the User runtime has only SELECT permission. The Identity runtime cannot read the table. No production Flyway SQL, HTTP route, JWT token issuance or application Entity is changed.

A new `SyntheticTenantMembershipLifecycleTest`, opt-in under the disposable PostgreSQL CI run, uses a **parameterized, exact-match JDBC EXISTS query** combining both tables. It checks:

- ACTIVE: two distinct synthetic object owners can access only their explicitly linked tenant/object tuple; numeric-ID equality, wrong tenant, missing/invalid IDs and missing tenant are denied.
- REVOKED: once the synthetic migrator revokes tenant A, the runtime can no longer admit that previous tuple, even though the object binding remains unchanged. Tenant B remains unaffected.
- EXPIRED: after tenant B is expired, both original memberships fail; no old permission is inferred from the still-present object rows.
- TEMPORAL CLOCK: the synthetic membership query compares expiry against PostgreSQL `statement_timestamp()`, not `CURRENT_TIMESTAMP`, which freezes at the start of a transaction. A deliberately long-running test transaction proves that transaction time remains stale after a synthetic deadline while statement time advances. This avoids one expiry-clock defect but **does not solve transaction-snapshot visibility, cache staleness, or a concurrent revocation race**.
- PRIVILEGE: the runtime has SELECT but not INSERT/UPDATE/DELETE/TRUNCATE on either binding or membership table. Direct role mutation attempts fail.
- RESTORE: the disposable backup/restore retains the revoked and expired membership records.
- Existing CI continues testing the role separation, synthetic signed-token denial, source-only release NO_GO and disposable TLS socket.

**Important evidence limit:** the tests use synthetic, intentionally crossed identifiers and do not establish that the actual Identity service issued a verified tenant or that production services authorize tenants. The real endpoints remain deny-by-default for consumer object access. The tests run the lifecycle across separate Java invocations and database commits; a cache-invalidation or concurrent revocation race has **not** been certified.

## Model choices requiring owner/DBA review before a real migration

1. Define authoritative tenant/organization IDs and how memberships are created, approved, revoked and audited. No request-supplied or self-asserted membership is authoritative.
2. Prefer explicit stable subject IDs and tenant membership relationships with foreign keys and unique constraints, not email matches or numerically equal unrelated table IDs. Cross-service schemas/permissions and PII boundaries need independent approval.
3. Define whether revocation is immediate and how cached object reads, distributed replicas and in-flight transactions handle stale membership. PostgreSQL READ COMMITTED takes a snapshot for each statement, while REPEATABLE READ can preserve older visibility across a transaction. `statement_timestamp()` fixes the expiration clock but **cannot invalidate an older MVCC row snapshot**. Design an explicit revocation linearization point, transaction/isolation policy and cache invalidation strategy; reject the claim that this synthetic proof guarantees instantaneous cancellation of already-running operations. A positive cached grant must not outlive the approved revocation policy.
4. Define expiry using server-side trusted time and transactional consistency. Deny missing, ambiguous, stale, expired and revoked records.
5. Enforce object binding for **all** list, lookup, create, delete and event paths; role ceilings and mTLS do not replace resource-level authorization.
6. Author separate, reviewed **versioned migrations**, legacy-baseline mapping, downgrade/restore procedures, and role grants. The synthetic schema must never be copied blindly into production.
7. Add staged tests for real Identity issuance/verification, tenant lifecycle writes, concurrent revoke/read, tenant-aware cache keys, network boundaries, old schema migration, auditable operator actions and restoration.
8. Reconcile stacked authorization/release PRs into a single approved promotion lineage. Preserve [release closure #322](https://github.com/teldigi5-wq/aetheris-platform/pull/322) and all remaining independent attestations.

## CI checkpoint

The new tests and database steps must pass on the **exact head of this draft PR**, including fail-closed release preflight. Do not reuse parent PR #358 CI as evidence of this new lifecycle work. Do not report production tenant authorization as complete.

**No live credentials, production data, migrations, owner grants, protected branch merge, or deployment are authorized by this candidate.**
