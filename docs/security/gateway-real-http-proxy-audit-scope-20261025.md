# Gateway real HTTP forwarding and fail-closed Audit write scope

**25 October 2026 — draft verification, stacked on Identity-issued contract PR #271.**

## Reviewed security gap

PR #270 added Gateway ADMIN-only JWT enforcement for User/Audit to match the independent backend authorization filters. One method-level inconsistency remained: `JwtAuthenticationFilter.requiredScope` returned **`services:read` for every `/api/events` HTTP method**, despite the Audit direct-service filter demanding **`services:write` for mutations**. This did not permit data mutation at the already independently guarded Audit backend, but was a gateway policy discrepancy and could forward read-only administrator mutation attempts unnecessarily.

This slice changes only the Gateway Audit method policy: GET/HEAD use `services:read`; POST/PUT/PATCH/DELETE require `services:write`. Identity's current ADMIN role does **not** grant `services:write`, so normal ADMIN Audit modifications fail closed with 403 at the Gateway. This does not invent an Audit mutation API, grant new permissions, rewrite issuer scopes or modify existing audit records. Focused Gateway tests verify all four mutation methods reject `services:read` and authorized signed ADMIN GET still passes.

## New real Gateway HTTP verification (NOT full deployed service integration)

`GatewayRealHttpProxyContractIT` starts the actual Gateway WebFlux application on a random localhost port, enables its real global JWT filter and Reactor Netty outbound proxy, and creates an ephemeral JDK `HttpServer` responder bound to `127.0.0.1:0`. It installs **two test-context-only routes** with order -200, overriding the production User/Audit DNS targets **for this test only**, with NO request rate limiter or circuit breaker on the test routes. Every test responder returns a distinct marker; a fallback or production upstream connection cannot count as an accepted proxy result.

The test requires the **exact ADMIN/DEVELOPER/API_CONSUMER/expired/tampered signed token files minted by actual Identity `JwtService.issue()`** in PR #271. The Gateway must:
- Return HTTP 200 through a real request to the User local responder for signed ADMIN, forward the unchanged Authorization JWT, replace client-forged `X-Aetheris-User-Id: 999999` with verified `42`, and set signed ADMIN role. Require distinct test-only upstream response header and body.
- Return 401 for anonymous, expired or signature-tampered tokens, 403 for legitimate DEVELOPER/API_CONSUMER requesting admin-only User API. For *every* denial, confirm responder invocation count did not increase.
- Route signed ADMIN Audit **GET** successfully through local proxy; reject **POST** with an ADMIN read-only token as 403 *before* outbound HTTP to the responder.

The original standalone JWT filter tests and both User/Audit actual loopback HTTP security tests are rerun in CI on Windows Server 2022 and Ubuntu 24.04. The sole additional feature is this isolated Gateway proxy QA + scope contract fix, not a production deployment.

## Truth boundaries
- The actual downstream server in this new test is an ephemeral **HTTP responder**, not the full User or Audit Spring Boot service. Those two services have already passed independent real HTTP tests and consumed Identity-issued signed JWTs in PR #271; **they have not yet been connected behind a single live Gateway and real infrastructure**.
- Test-context routes bypass the production Redis rate limiter and circuit breaker. This result cannot establish Redis, circuit breaker, service discovery, Docker/Kubernetes CNI, TLS, ingress or container isolation behavior.
- All test-only JWTs use a literal synthetic secret and do not represent real production logins. No customer accounts, credentials or production datastore are accessed.
- Object ownership/tenancy, issuer/audience checks, real key rotation/revocation, safe database migrations and full deployment remain open. Issue #251, Stage45 13 NO_GO blockers and nine release gates are not closed.

## CI acceptance

New `.github/workflows/gateway-real-http-proxy.yml` runs full existing Gateway and direct-backend Maven suites, plus the explicitly invoked real-HTTP Gateway test, on Ubuntu and Windows. Before crediting progress, require **successful exact-head CI on both systems** with three real HTTP proxy scenarios plus method-level authorization tests. Never weaken JWT checks or claim the responder stands for a production backend service.

This remains a stacked, reversible **draft pull request**; protected `main` and production are unchanged.
