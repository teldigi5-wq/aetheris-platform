# S0-E6H — Identity-only prospective Gateway mTLS workload gate

**30 October 2026 · Stage45 NO_GO · stacked DRAFT · no HTTP exchange route · no production rollout.**

## Verified origin

Direct parent: [Platform PR #350](https://github.com/teldigi5-wq/aetheris-platform/pull/350) exact SHA `87b5eadf6fe4304fd68e6257bdaee52719b71093`, [Actions #40897686565](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40897686565): real disposable PostgreSQL 16 6-worker concurrency/rollback, actual Platform↔Runtime RSA signer/verifier and Runtime real HTTP quarantine **48/48 targeted tests passed**. This parent also corrected the Spring transactional proxy blocker on the internal exchange service. All earlier security PRs remain draft/unmerged.

Prior separate User-service mTLS research candidate [Platform PR #346](https://github.com/teldigi5-wq/aetheris-platform/pull/346) contains a client-certificate/SPIFFE policy and a minimal genuine Tomcat HTTPS test. **That prior User-service code is not merged into this Identity branch, and its successful synthetic/transport results cannot be credited to Identity.** This stage uses the same conservative checks adapted to the Identity-specific use case.

## What the two new production-scanned components provide

1. `RuntimeExchangeGatewayWorkloadPolicy` is an operator-configurable Spring `@Component`, **disabled and denying all requests by default**. There is no new endpoint, auth filter or network listener. To enable it, the operator must **explicitly** set `aetheris.runtime-exchange.gateway-mtls-required=true`, an exact expected SPIFFE ID `aetheris.runtime-exchange.gateway-spiffe-id`, `server.ssl.enabled=true`, `server.ssl.client-auth=need` and a nonempty explicit `server.ssl.trust-store` or `server.ssl.bundle`. Missing/invalid/partly enabled settings refuse application startup rather than falling back to a forwarded identity header.
2. At future **server-verified HTTPS transport only**, the policy checks servlet `jakarta.servlet.request.X509Certificate` peer certificate attribute, TLS-secure request scheme, leaf validity, non-CA status, digital-signature key usage, `clientAuth` EKU and **one exact URI SAN** matching the expected Gateway SPIFFE identity. Caller-provided `X-Forwarded-Client-Cert`, `X-Aetheris-Gateway-Identity` and owner/user headers are never used for validation.
3. `RuntimeAudienceWorkloadBoundExchange` is a separate, internal-only Spring `@Service` adapter that **refuses** before invoking E6F/G `RuntimeAudienceSessionExchangeService.exchangeUsingSingleUseRefreshCredential` unless the policy accepts the current HTTPS peer. That downstream exchange still uses the real PostgreSQL-tested atomic single-use refresh credential and current account role before signing a short-lived Runtime-audience RSA token; both credentials remain redacted from the exchange record string representation. The underlying original internal exchange service still exists as its own bean, so **do not assume all internal Java calls are now forced through this new adapter**. No route calls either bean.

## Testing and proof boundaries

`RuntimeExchangeGatewayWorkloadPolicyTest` uses Spring `MockHttpServletRequest` and synthetic X509Certificate mocks: disabled/partial settings reject; wrong SPIFFE ID, unsecure transport, CA leaf, expired cert, wrong/no client EKU, missing digitalSignature, DNS-only/ambiguous SAN and forwarded-header identity forgeries all fail before any session method call. Exact matching *mocked* certificate evidence permits invocation of the internal adapter, and downstream single-use session refusal is propagated without credential disclosure.

The SHA-pinned dedicated CI will run these alongside **real PostgreSQL 16 transaction tests**, the six exact-source signer/verifier roundtrip tests (compiled with the unchanged pinned Runtime source), the existing Identity/browser/refresh/session/issuer suites and the pinned actual Runtime JWT + real HTTP quarantine tests. Exact-head GitHub Actions SUCCESS is mandatory before calling E6H's development milestone verified.

**Important: synthetic servlet attributes are NOT cryptographic TLS proof.** A hostile caller inside a mock context can set the attribute. The actual server connector must independently validate client certificates against a **restricted operator trust anchor** with `client-auth=need`; only then does the servlet attribute become reliable certificate provenance. **This stage does NOT start an Identity HTTPS socket, verify an actual Identity mutual TLS handshake, configure a deployed Gateway, rotate client certs, perform CRL/OCSP, or expose a token exchange endpoint.** A string saying a truststore is configured is not proof that the file exists or that its CA is trustworthy.

## Release blockers remain

- An approved true Identity HTTPS/mTLS transport, authenticated Gateway workload path and real browser session exchange design (CSRF protection, origin checks, independent session revocation, rate limits and audit) are still absent.
- Revoking the refresh credential still **does not invalidate an already issued 5-minute RS256 token** in Runtime. Online `jti`/session revocation and key lifecycle are E7 blockers.
- Runtime full `/api` and `/actuator` blanket 404 quarantine in the draft chain would cause availability disruption if prematurely deployed. Task/Mission object ownership, durable PostgreSQL migration, RLS, customer route-specific grants and per-user SSE remain absent.
- Separate Platform dashboard npm vulnerability [issue #240](https://github.com/teldigi5-wq/aetheris-platform/issues/240) remains a release blocker, as do Runtime [issue #23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23) and Platform [issue #251](https://github.com/teldigi5-wq/aetheris-platform/issues/251), which stay OPEN.
- **S0-E6 is IN PROGRESS, six planned engineering packages E6–E11 remain including E6. All 9 independent Stage45 release gates remain uncertified, physical owner-PC verification pending. Production NO_GO.**

No protected-main merge, no secret material added, no production deployment, no real customer token issue, no global SSE, no Task/Mission route grant and no certificated Runtime pin change. The project is not release-certified.
