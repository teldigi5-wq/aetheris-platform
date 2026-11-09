# Aetheris XERJ: real Gateway → Tomcat mTLS + pinned RS256 (isolated review)

Child of draft #384 at source `a34a90f4f5b731496cc8e3d041e673398359e611`. All tests and TLS credentials are generated inside a hosted CI runner. No production workloads or existing Compose projects, DBs, credentials or Kubernetes resources are changed. XERJ #376 is HOLD; Stage45 NO_GO.

## Source fixes included
- Gateway's existing two User routes and Audit route now accept explicit operator-supplied HTTPS backend URI overrides with their previous HTTP defaults unchanged, without removing filters, rate limits or circuit breakers.
- User/Audit JWT issuer and audience were incorrectly nested under `aetheris.gateway`, so non-default JWT settings were silently ignored. Moved them back to `aetheris.jwt`; a CI contract verifies correct nesting and a synthetic issuer/audience uses values different from defaults.
- Explicit `xerj-mtls-review` profile uses the maintained Spring Cloud Gateway **5.x server.webflux.httpclient.ssl** namespace with only an operator-provided client PKCS12 key and trust-pinned CA cert, and `use-insecure-trust-manager=false`. This profile is inactive by default; not a production migration.

## Evidence required before any claim
1. Fresh hosted RSA-3072 issuer signing key, available only to Identity; Gateway/User/Audit receive only public verifier.
2. Fresh local-only root CA, valid one-day User and Audit server certificates with their exact DNS names, and Gateway client cert with exactly one canonical SPIFFE URI, clientAuth EKU and DigitalSignature.
3. Real Tomcat backends on HTTPS port 8081/8083 with `server.ssl.client-auth=need`, external CA truststore, operator-injected private PKCS12 server stores and mTLS policy enabled, not synthetic servlet certificate attributes.
4. Real Spring Cloud Gateway forwarding to both HTTPS backends, using client PKCS12 cert, trusted CA and verified hostnames; Identity and Gateway client-facing lab route remain loopback HTTP only (NOT production ingress).
5. Prior 32 real RSA service/DB/event checks continue to pass under mutual TLS; extra negative socket probes: missing client cert even with ADMIN JWT, spoofed forwarding headers cannot replace a client certificate, trusted but wrong SPIFFE URI gets 403, untrusted server CA fails TLS, DNS-name mismatch fails TLS, correctly trusted client with invalid JWT gets 401, and correct client cert + Identity-issued RSA JWT returns 200. Every failing check blocks CI.
6. Only sanitized boolean/check report retained; private/signing key material, PEM, JWT, cookies, raw responses and application log files never uploaded. Disposable container volumes destroyed after tests.

This is an honest **real TLS handshake in a disposable hosted lab**, not production PKI, SPIRE/SPIFFE attestation, revocation, HSM custody, Kubernetes mesh enforcement, tenant isolation, database migration approval or Stage 45 signoff. Existing mTLS PR #346 remains independent/unmerged; no duplicate JWT filters introduced. Live XERJ cannot be repinned or submitted until source rights and full security approvals.
