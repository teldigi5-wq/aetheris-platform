# RS256/mTLS tenant authorization — exact-head review checkpoint

**Status: review-only, no production authorization, release NO_GO.** This evidence belongs to [draft PR #358](https://github.com/teldigi5-wq/aetheris-platform/pull/358), based on draft [PR #345](https://github.com/teldigi5-wq/aetheris-platform/pull/345).

## Why consumer ownership remains denied

The actual Identity service issues signed `uid`, `sub`, `role`, and `scopes` claims, but does **not** issue a verified `tenant_id` claim. The IdentityAccount and User tables have independent IDs, and the User table does not have a durable, production-approved ownership/tenant mapping. Neither an equal numeric ID, matching email, a caller-supplied tenant header, nor a synthetically signed tenant claim proves object ownership.

The candidate User filter therefore **does not grant API_CONSUMER access** to `GET /api/users/{id}`, even if `uid` matches the requested ID and the token or request contains a purported `tenant_id`. The candidate Audit filter similarly does not grant consumer access to `GET /api/events`. These are deliberate deny-by-default constraints; they are not a complete multi-tenant authorization implementation.

## New acceptance work

- Actual User and Audit MockMvc tests use **ephemeral RSA keypairs** to sign synthetically constructed RS256 tokens with unverified tenant claims. They assert that authorized cryptographic signatures, claimed tenant IDs, matching numeric IDs, and spoofed `X-Aetheris-*` headers **do not grant consumer permissions** or reach the mocked business services.
- The PR workflow still runs its full four-module Maven test suite and fail-closed release preflight.
- The workflow now also exercises the existing **real disposable PostgreSQL 16** rehearsal at the **exact PR head**, not only the earlier parent PR #345 head. That script creates six scoped database roles, grants runtime-only access, and tests explicit cross-tenant `(identity_uid, user_id, tenant_id)` binding queries under a synthetic, CI-only schema. It runs both ownership test classes with their explicit opt-in system properties, verifies data separation and permission denials, then rehearses synthetic restore and cleanup.
- The existing disposable **Gateway Reactor Netty TLS socket** proof is also run explicitly on the exact PR head using synthetic CA and SPIFFE certificates. This is network-protocol evidence for a test socket, not evidence of deployment-level identity or real Gateway-to-backend access.

## Remaining design/approval gates

1. Design approved persistent organization/tenant membership and Identity-to-User resource bindings with migration, uniqueness, lifecycle, revocation, immutable audit records, and per-tenant isolation.
2. Establish a trusted source for assigning tenant membership. Never accept tenant identifiers from arbitrary request headers, request bodies, account emails, or matching numeric keys.
3. Issue tenant-targeted short-lived access tokens **only after an authoritative membership check**, or resolve authorization server-side by verified subject and tenant. Bind issuer, single audience, key ID, role ceiling, tenant, and revocation policy. Do not make a test-only synthetic tenant claim a production grant.
4. Implement resource authorization in the User service at the data-access boundary; evaluate cache keys, list endpoints, IDOR/BOLA, object creation/deletion, concurrent membership changes, and authorization failure semantics.
5. Rehearse fresh and legacy schema baselines, read-only runtime DB roles, backup/restore and rotation before any production migration. Preserve existing data and make rollback explicit.
6. Execute end-to-end network/mTLS, Gateway routing, Kubernetes CNI, authn/authz, tenant isolation and fault injection on a staged environment before owner approval.
7. Reconcile the several stacked security branches into **one reviewed release lineage**. Retain the separate release closure under [PR #322](https://github.com/teldigi5-wq/aetheris-platform/pull/322); never treat a green test as release certification.

**Evidence policy:** Do not claim these newly added tests passed until the latest-head workflow result is inspected. No production DB, live token, customer identity, secret rotation, protected-branch merge or deployment is authorized by this candidate.
