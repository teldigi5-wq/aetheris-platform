# S0-E6I — real disposable Identity Tomcat HTTPS mutual-TLS probe

**30 October 2026. Stage45 NO_GO. DRAFT ONLY. No actual Identity token-exchange endpoint, production deployment or access grant.**

## Exact verified parent

- [Platform DRAFT PR #351](https://github.com/teldigi5-wq/aetheris-platform/pull/351) @ `0b30afdaa49e2aebde163cd5c1e13d142a150161` was the last verified checkpoint: [CI #40983214935](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40983214935) passed **43 Platform security/PostgreSQL/JWT tests + 11 pinned real Runtime verification/HTTP tests = 54/54**.
- The prior E6H stage created a **default-deny, Spring-scanned Identity `RuntimeExchangeGatewayWorkloadPolicy`** and an internal `RuntimeAudienceWorkloadBoundExchange` adapter. However its six certificate tests used synthetic servlet request attributes; those alone cannot establish authenticated TLS peer provenance.
- A separate, unmerged User-service research candidate [PR #346](https://github.com/teldigi5-wq/aetheris-platform/pull/346) already had a useful real Tomcat certificate fixture. The current stage adapts the technique to the actual **Identity** security policy, without merging or altering that User branch.

## Added E6I evidence

`RealIdentityHttpsMutualTlsWorkloadTest.java` uses a **minimal disposable embedded Tomcat HTTPS connector** on an ephemeral port, configured `Ssl.ClientAuth.NEED` with one restricted, synthetic CA as its trust anchor. The deployed Identity application's full beans and database are **not** started. Its **only servlet** is an isolated test-only `/__e6i-private-test-probe` route in the JUnit nested configuration, not an Identity production controller. The probe invokes the **real production Identity `RuntimeExchangeGatewayWorkloadPolicy`** and `RuntimeAudienceWorkloadBoundExchange` but uses a mock `RuntimeAudienceSessionExchangeService` to prevent token signing/refresh access. An accepted probe returns HTTP 204, never credentials.

`scripts/security/run_identity_e6i_tls_lab.sh` runs only when the disposable-only environment marker is set. It generates an ephemeral RSA synthetic CA, server certificate for `localhost`, exact Gateway URI SAN client certificate `spiffe://aetheris.test/ns/security/sa/gateway`, wrong-SPIFFE client signed by that same CA, and an untrusted client signed by an unrelated CA. The private material is stored inside a 0700 `mktemp` directory, private keys/PKCS12 files have 0600 permissions, and an EXIT trap deletes them all. The per-run keystore password is randomly generated inside the ephemeral CI process and is never committed or printed.

The actual real HTTPS handshake tests must prove:

1. **Trusted, CA-validated exact Gateway SPIFFE certificate** successfully completes HTTPS and calls the **internal mock only**, returning HTTP 204.
2. **Missing client certificate**, even with forged forwarded identity headers, is rejected at the TLS handshake, never reaching the servlet.
3. **Trusted CA but wrong SPIFFE URI SAN** completes TLS yet returns HTTP 403 from the real Identity policy, with no session call.
4. **Client signed by unrelated CA** is refused during the TLS handshake, never reaching the servlet even if identity headers claim Gateway identity.

The SHA-pinned E6I CI runs four mandatory socket tests and parses their Maven Surefire XML report to reject test skips, then reruns all E6H Identity/browser/refresh/JWT tests, real disposable **PostgreSQL 16** six-contender single-winner/rollback tests, actual signed Platform-to-exact-Runtime issuer/verifier JUnit fixture, pinned real Runtime signature verifier, and pinned Runtime full-app real loopback HTTP 404 quarantine checks. Both checkout SHAs are asserted; the Runtime source reference remains `c0618c827b326eb6a5bd02fa2a5e9365d593b4c6`.

## Strict limits; production remains NO_GO

- The server is a **minimal test-only HTTPS Tomcat** using production Identity certificate policy. It is **not** the entire real Identity Spring Boot service, and does not test real deployed Gateway route wiring, client identity renewals, CRL/OCSP revocation or Kubernetes trust.
- There is still **no production HTTP token-exchange endpoint** or route that calls the internal service. The underlying session-exchange bean remains independently callable by trusted in-process Java code; the adapter is **not an enforced universal call boundary**. Workload authentication + user session exchange + rate limiting/CSRF/origin checks must be designed and separately verified before exposing endpoints.
- Revoking the refresh token **does not immediately invalidate already-issued five-minute Runtime JWTs**. Runtime needs independently enforced, durable session/`jti` status or equivalent online authorization plus key rotation. A JWT signature is not an owner/tenant binding.
- Task/Mission and `/api`/`actuator` Runtime quarantine remain as draft, and the unconditional E4 404 may break legitimate service availability if deployed. RLS, real production schema migration, tenant ownership and per-user SSE are still absent.
- Dashboard dependency findings [Platform issue #240](https://github.com/teldigi5-wq/aetheris-platform/issues/240), Platform security [#251](https://github.com/teldigi5-wq/aetheris-platform/issues/251) and Runtime security [#23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23) remain OPEN.

**S0-E6 remains IN PROGRESS. Six major security engineering packages E6–E11 (including unfinished E6) and all nine independent Stage45 release certifications remain; production Stage45 NO_GO.** No protected main merge, production deployment, operator/client secrets, customer tokens, API access or runtime release-pin promotion. Only a completed successful **exact-head CI** will verify this development substage.
