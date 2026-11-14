# Issue #251 — Real loopback HTTP service authorization regressions

**Draft research test, 25 October 2026.** Stacked on the User/Audit JWT protection in PR #267 and signed integer-ID hardening in PR #268. No protected branch merge, production traffic or real credentials touched.

## Why add these tests

Earlier `DirectApiJwtFilterTest` classes used standalone MockMvc with the filter manually attached. That proves filter logic but does **not** prove that the actual embedded Spring Boot HTTP server registers the filter and protects direct routes when the Gateway is bypassed.

`DirectApiLiveHttpTest.java` for **both** User and Audit boots the real service on a random port and sends genuine `java.net.http.HttpClient` HTTP requests to `127.0.0.1`. The filter is discovered by normal Spring component registration rather than manually passed into MockMvc.

- Anonymous User/Audit GET: HTTP 401. Forged `X-Aetheris-User-Role` and `X-Aetheris-Scopes` without the original Bearer: 401.
- Invalid signature or expired token: 401. Fractional signed `uid=42.5`: 401.
- Valid signed but incorrect administrative role or read scope: 403.
- ADMIN + correct read scope: 200, reaching a **mock** business service, not reading customer records.
- User DELETE without Bearer: 401. Signed incorrect role or only read scope: 403. ADMIN + `users:write`: 204, invoking a mock instead of deleting a row.
- Audit POST without a Bearer or without a write scope: 401/403 before it can reach any handler.

**Isolation:** User uses its pre-existing test-only H2 datasource and replaces `UserService` with `@MockitoBean`. Audit adds a test-scoped H2 driver and an H2-only test config, disables Flyway and RabbitMQ listeners, and replaces `AuditEventStore` with `@MockitoBean`. All JWTs use a literal **test-only** secret and synthetic invalid-domain identities. No production passwords, Docker daemon, PostgreSQL, Redis, RabbitMQ or Kubernetes cluster is needed for these HTTP requests.

The existing `backend-direct-jwt.yml` executes all User/Audit Maven tests on Ubuntu and Windows at the **exact PR head**. Passing these tests demonstrates auto-registered Spring Servlet filter behavior on disposable loopback ports, not actual Docker/Kubernetes network reachability or complete end-to-end Gateway/Identity token compatibility.

## Explicitly remaining NO_GO

1. The real image/container-level direct access check must deploy disposable services, isolate shared networks and verify unauthorized container/Pod access via actual container-to-container connections.
2. Gateway/Identity real issued JWT integration (with issuer/audience, role/scope matrix, size limits, token rotation/revocation and workload credentials) is pending.
3. User resource rows and Identity account IDs are different models: signed `uid` does **not** prove ownership of `/api/users/{id}`. Continue ADMIN-only until tenancy and row-level authorization is designed and tested.
4. The audit write method has no controller mapping; a 403 in the filter does not imply a valid authorized audit write operation.
5. No Stage45 GO, no nine release gate closure and no production certification can be inferred from local test results.

Do not relax filters to make integration green; fix test wiring and API compatibility independently. Preserve a separate stacked draft PR and reversible minimal changes.
