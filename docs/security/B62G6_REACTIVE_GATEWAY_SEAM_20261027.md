# B62-G6 — Real Gateway Reactive JWT Revocation Seam (test-only)

**27 October 2026 · REVIEW DRAFT · NOT INTEGRATED · NO_GO**

Dependency: B62-G5 PR #310 exact green head `43d8c2ddbe9e51880d51efb4cbade0f07c18256a`. This stage upgrades the actual Spring Cloud Gateway JWT filter **test seam only**. It does **not** enable a production emergency revocation mechanism.

## Motivation and design

The Gateway owns a **reactive** nonblocking WebFlux/Netty request path. Blocking JDBC or calling `.block()` on the Netty event loop would be a dangerous regression. Unlike the synchronous User and Audit servlet filters, a Gateway proof needs a lazy `Mono<Boolean>` revocation callback with a strict timeout, no cached permit decision, and fail-closed handling.

The genuine `JwtAuthenticationFilter` now has a package-private `TestOnlyReactiveRevocation` constructor whose supplied callback is accepted **only for RS256**. The existing public constructors and `@Autowired` production constructor call the clock-based constructor, which installs `null`. No environment flag, external token header, property, Spring bean, JDBC URL or runtime endpoint can enable this callback. Live Gateway remains functionally unchanged in the absence of the seam.

The test-only callback is reached **after** genuine pinned JJWT RSA signature verification, signed kid, exact audience, issuer, issued-at/expiry bounds, positive signed principal and required method-scoped permission. It receives only validated principal ID, pinned issuer, verified signing kid and signed `iat` epoch. In the test-only branch, invalid principal IDs are forbidden even for Orchestrator routes. `Mono.defer` ensures the callback runs on subscription. `timeout(Duration.ofMillis(200))`, `onErrorReturn(false)` and `defaultIfEmpty(false)` reject false, empty, exception, synchronous throw, null publisher and timeout. **No identity forwarding, synthetic gateway identity headers, or rate-limit verified-user attribute is written before an allow decision.** The forwarded path preserves existing header-spoof stripping.

JUnit tests generate ephemeral 3072-bit RSA keys and genuine JJWT RS256 claims and drive the real Gateway filter using `MockServerWebExchange`. They test asynchronous allowed response, delayed publisher, offline/error/empty/never-completing publisher denial, forged headers, bad signature, signer kid, issuer/audience, fractional UID, expired credentials and insufficient role/scope. Existing complete Java reactor and direct User/Audit filter regressions also remain required in exact-head CI.

## Why this is still not production revocation

**NOT INTEGRATED**: Actual Gateway, User and Audit service requests do not access the durable revocation store. The Gateway seam does not query PostgreSQL, Redis, an external HTTP authority, or any connected production database. Its 200 ms timeout is an experimental test guard, **not an adopted production latency budget**. These are real Java filter tests but do not constitute multi-node HTTP/JWT revocation certification.

Production adoption still requires independently reviewed **database migration** history, backups/restore (#242), audited emergency writer access and **owner approval**, dedicated least-privilege service identities, well-bounded **nonblocking** network clients with backpressure, primary **read-after-write** semantics avoiding stale replicas, cross-node outage/clock and load tests, true owner/tenant resource authorization (#251), consistent critical incident handling and rollback without trusting compromised keys. Parallel **certificate** trust release candidate #309 also needs reconciliation with the existing B62 draft stack. Syntra E3.9 and E4–E9 remain unfinished and all nine runtime release gates remain uncertified. Stage45 **NO_GO** applies.

No protected-main merge, production service activation, real key replacement, actual customer database migration, or deployment is authorized by this CI success.
