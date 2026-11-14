# B62-G5 — Real Servlet JWT Filter Revocation Seams: Test-only Integration

**27 October 2026 · REVIEW-ONLY · NOT INTEGRATED · NO_GO**

This slice stacks on B62-G4 PR #308 verified head `cbe90ee34d8216b602663b43a01f131fdfab5667`. It deliberately advances from an isolated Identity test class to the **real** User and Audit servlet JWT filter code, without changing the active Spring wiring.

## Implemented

Both direct-service filters now have a package-private constructor accepting a `TestOnlyRevocationDecision` interface. The real, `@Autowired` production constructor delegates with `null`; thus no revocation lookup is activated in deployed code. No feature toggle, Spring bean, env option, API endpoint or JDBC URL exposes a way to enable this seam. The extra constructor refuses non-RS256 mode to avoid passing untrusted HMAC signer identities to a proof reader.

In **test-only** configurations, the real servlet filter verifies RSA-3072 JJWT signatures, pinned public key / signed kid / issuer / single audience, exact positive signed UID and temporal claims, then enforces independent ADMIN role and method-specific scopes **before** calling the injected revocation reader. The callback receives only validated principal ID, pinned issuer, signed signer kid and issued-at epoch; never bearer bytes, request-selected URLs, tenant aliases, or unchecked headers. A false result or any exception denies HTTP 401, and neither case reaches a business controller. Invalid signatures, signed UID, issuer/audience, role/scope never reach the callback.

The JUnit 5 tests create ephemeral in-memory RSA-3072 key pairs, sign genuine synthetic JJWT access tokens, and pass them through the actual UserController and AuditController filter chains via Spring MockMvc. They verify valid accept, deny, timeout/outage, invalid signature and altered kid, incorrect issuer/audience, fractional identity, expired JWT, missing authorization scopes, and continued refusal to enable a proof callback in HMAC mode. No sensitive data, full JWTs or production keys enter CI logs.

## Critical non-goals and release blockers

**NOT INTEGRATED:** The live service filters have **no** production revocation reader. These test-injection seams do not connect to PostgreSQL or imply customer tokens are revocable now. A synchronous JDBC check must **never** be transplanted into the reactive **Gateway** Netty event loop. Gateway will require a separately designed nonblocking bounded lookup with strict backpressure and denial on exception/unavailability, followed by real HTTP testing.

Before any real enforcement: obtain owner approval, historical database migration and backup/restore governance (#242), production **read-only workload identities**, append-only administrative emergency approval and audited writer path, reliable primary read-after-write with no stale replicas, tenant-safe principal binding, explicit method/object authorization (#251), outage/latency budgets and multi-node replay testing. Reconcile parallel certificate-trust release draft #309. Syntra E3.9 and E4–E9 and nine release-certification gates remain separate unfinished work.

The stack is still **NO_GO**, draft/unmerged. Never call test-only callback injection a production feature or merge this draft as an emergency kill switch.
