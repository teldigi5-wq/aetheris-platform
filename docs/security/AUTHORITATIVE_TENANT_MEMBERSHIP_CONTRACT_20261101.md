# Aetheris authoritative tenant authorization — proposed contract (review only)

**Status:** PROPOSED / NO_GO; no production API or migration authorization.  
**Review base:** Platform draft PR #367; Runtime issue #23 remains separate.  
**Source evidence:** Identity `JwtService` currently issues authenticated `uid`, `sub`, `role`, `scopes`, optional `iss` and `aud`. No authoritative `tenant_id` is issued. `identity_accounts.id` and `users.id` are independent identities. The existing User resource API intentionally forbids consumer object access. These are verified source observations, not a rollout.

## Trust boundary and contract

1. **Authenticate the principal** using a locally verified, pinned RS256 signature; exact issuer; single intended audience; accepted `kid`; bounded `iat`/`exp`; positive stable subject UID; and role/scope ceilings. Workload mTLS (when approved) authenticates *the caller workload*, not object ownership.
2. **Resolve the tenant independently** from a reviewed, durable tenant/membership registry. A signed `tenant_id`, user email, equal Identity/User numeric IDs, `X-Aetheris-*` header, target URL, or caller-provided project name is never, alone, an entitlement. A tenant selector may constrain the lookup but must not create a membership.
3. **Authoritative membership and exact binding** must both be current at the point of admission, in a parameterized query or equally proven atomic policy decision. Deny if a subject, tenant, membership, resource binding, requested operation, schema version, or authority is missing or ambiguous.
4. **Lifecycle**: membership state must be ACTIVE and unrevoked, with expiration checked using trusted database time. A previously signed but unexpired token MUST be denied by a fresh membership check after revocation. Invalid JWT/unknown subject => 401, authenticated disallowed resource => 403 or deliberate non-disclosing 404; never leak cross-tenant existence.
5. **Freshness**: READ COMMITTED can observe a committed revoke on a subsequent statement. REPEATABLE READ may keep an older MVCC snapshot even though `statement_timestamp()` advances. Define the permitted window for in-flight operations; don't promise instantaneous cancellation without locks/serializable coordination or a separately validated revocation point. No positive-grant caches until tenant, principal, resource, privilege, membership version and a bounded revocation invalidation mechanism are approved.
6. **Data-access boundary**: collection listings, individual records, creates, updates, deletes, audit events and streams must scope queries by the verified identity and authoritative tenant. Do not authorize in a controller and subsequently call an unscoped cache/repository. BOLA/IDOR negative tests are mandatory.
7. **Provisioning**: only a vetted administrative authority may assign, change, or revoke memberships. Service runtime connections get the least-privileged reads and scoped DML needed for their domains; no self-provisioning of memberships from bearer payloads or unaudited background jobs.

## Proposed schema/ownership questions — not a migration

- Select the canonical source-of-truth for organizations, subject membership and object binding, including who can write and revoke them. Does the trust boundary reside in Identity, a separate authorization service, or a dedicated restricted database schema? **Undecided.**
- Decide stable tenant UUID assignment and uniqueness, membership version or status transitions, FK relationships to vetted account/resource identifiers, cross-schema role grants, and append-only auditing.
- Determine how legacy `users` rows are quarantined until an approved mapping can be established. No inference from matching email, IDs, or existing JWT claims.
- Design versioned forward-only migrations, historical-schema inventory, backup/restore/rollback, read-only runtime privileges and migration-user split. Never auto-apply synthetic PostgreSQL fixture SQL to a production database.
- Evaluate server-side tenant selection and signed targeted-token issuance *only after* a trusted membership check. Target-scope tokens reduce blast radius but cannot alone prove continued membership.

## Executable proof and what it does NOT show

The isolated synthetic signed-token and concurrency tests use disposable PostgreSQL 16, random synthetic HMAC keys, a restricted `user_runtime` read role, independently authorized synthetic migrator writes and deliberately crossed User/Identity IDs. They assert:

- Signature, issuer, one audience, role, scope, UID and exact binding.
- Active/unrevoked/unexpired membership is required **in the same SQL query**.
- **The same original JWT** loses admission after a *committed* revoke on a new READ COMMITTED statement.
- REPEATABLE READ can still observe a stale grant within a prior transaction snapshot; ending it restores denial.
- Fake signatures, wrong audiences, forged/misbound tenant identifiers and equal unrelated numeric IDs fail.

**Limitations:** These tests are not production issuer/tenant integration, don't validate actual schema provenance, Kubernetes networking or a release, and don't make consumer routes live. CI proof is required at the exact test-branch commit; logs must not expose JWTs or secrets.

## Required approvals before a live grant

- [ ] Tenant steward and security owner approve registry source and admin/revocation workflows.
- [ ] Identity and User resource-ID domain mapping + versioned PostgreSQL migration reviewed against real legacy baseline.
- [ ] Approved membership write API, revocation audit, data lifecycle and recovery demonstrated.
- [ ] Verified principal/tenant propagated to a **repository-level** authorization decision across all routes; unverified claims remain denied.
- [ ] Cache, concurrency, retry, race, expiry, write atomicity and streaming isolation tested at real service boundaries.
- [ ] PostgreSQL role grants, network/mTLS, hostile cross-tenant requests, JWT rotation and fault-injection pass independently.
- [ ] Exact release source reconciled, independent checks/reviews and Stage45 attestations complete.
- [ ] Historical secret findings and active-key reuse assessment closed separately, before XERJ permission.

**Release hold:** Platform issue #251 open, Runtime issue #23 open, XERJ BLOCKED, Stage 45 NO_GO. No owner approval, production migration, merge, credential rotation or deployment is authorized by this document.
