# Aetheris User API account-bounded tenant security — DRAFT NO_GO

**Parent:** draft security #385 at exact passing commit `e50ffb14c9a442c85c61ec176c13dd0a0cec0703`. This child branch is a separately reviewable preview; no protected main, real database, production access, HSM/private keys, deployment or upstream XERJ candidate is changed.

## Why this is needed
Historical `identity_accounts.id` and `users.id` are separate auto-generated numeric sequences with **no ownership relationship**. Equating the two, trusting an emailed user ID, using an `X-Aetheris-Tenant-Id` header, or using a tenant from an HTTP JSON request would create an exploitable authorization bug.

## Exact narrow behavior
- When a **new** Identity account is registered, the server generates a cryptographically random UUID and persists it as `identity_accounts.tenant_id`; the signed JWT includes a canonical `tid` claim. Separate accounts start with separate tenant IDs. Existing accounts have **NULL** tenant assignment until an independently reviewed data migration; no inference or automatic backfill.
- `API_CONSUMER` receives only two new scopes: `tenant:users:read` and `tenant:users:write`. Those scopes NEVER permit historical global `/api/users` or Audit `/api/events`. Global `ADMIN + users:read/write` and `ADMIN + services:read` rules remain unchanged.
- Tenant endpoints live under `/api/users/tenant` and `/api/users/tenant/{id}`. Both Gateway and independent User backend require a signed `API_CONSUMER` token with a canonical UUID `tid` and the correct read/write scope. User backend sets a private Servlet request attribute ONLY after JWT verification; controller never takes a tenant from a header/query/body.
- `users.tenant_id` stores ownership on newly created tenant records. List, read-by-ID and delete lookup use `WHERE tenant_id = verified_tid` (via Spring Data JPA method signatures) and never load a global cached record for tenant reads. Missing foreign rows become indistinguishable 404s, with no cross-tenant delete or Rabbit event. Both tenant writes evict the existing ADMIN global caches.
- Legacy unowned `users.tenant_id IS NULL` rows remain accessible **only** through global ADMIN routes. Tenant accounts cannot see them.

## Real verification
The exact-head workflow first runs targeted signed JWT, Gateway, backend and JPA-boundary unit tests. Then a uniquely named hosted disposable PostgreSQL/Redis/RabbitMQ/Java/mTLS stack replays all **47 existing** real RSA/mTLS/AMQP checks and registers two separate accounts to prove independent token tenant IDs, scoped tenant data creation/list/read/delete, failed cross-account foreign-ID reads/deletions through Gateway AND direct mTLS backend, forged header denial, and unchanged global API restrictions. The migration smoke script applies both additive SQL files twice in a separate synthetic legacy database, verifying old rows remain unassigned. No source credentials or JWTs are uploaded. Test outcomes are UNKNOWN until the exact-head CI is inspected.

## Important explicit limitations — this is NOT yet broad enterprise tenant isolation
- The first version is **one server-owned tenant UUID per newly registered account**. There is no audited tenant organization, invitations, shared membership, tenant role administration or data tenancy for other domains.
- `users.email` still has historical **global uniqueness**, not `(tenant_id,email)` uniqueness. The tenant endpoint can return a generic conflict for an email held in another account. Do not claim complete privacy or migrate uniqueness constraints without a separate impact and safe data migration.
- Global **super-ADMIN** may inspect across all accounts; this is explicit privilege and requires independent operational approval and audit.
- Audit event messages currently do **not** include a trustworthy tenant ID, so the Audit API remains strictly global ADMIN-only. Event retention, outbox idempotence and tenant audit attribution are unresolved.
- The additive SQL is intentionally *not* wired into automatic production migration. Real environments using Hibernate `validate` will require a controlled, backed-up schema migration first. No production database has been migrated, and no rollback rehearsal exists.
- Signed JWTs carry tenant IDs but no real-time account-revocation lookup. Rotation/revocation windows and HSM custody still need separate gates.
- Real mTLS has been demonstrated only in disposable hosted CI; production TLS/cert chain, CNI/NetworkPolicy, SQL least privilege, Redis internal authentication, image scanning, Stage45 attestations, historical credential clearance and human corpus rights/consent remain OPEN.

**Status:** Aetheris Stage45 `NO_GO` | XERJ #376 `HOLD`. Do not merge, deploy, repin or publish until all relevant security and rights checks pass.
