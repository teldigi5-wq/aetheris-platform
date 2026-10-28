# Gateway outbound mutual TLS profile — review-only candidate

**28 October 2026 · stacked on draft #343 · Stage45 NO_GO**

## What this actually implements

The existing Spring Cloud Gateway 4.1 / Reactor Netty client supports
native outbound PKCS#12 client-key and pinned X.509 trust configuration.
This candidate adds `application-gateway-mtls.yml`, which is **inactive
unless `gateway-mtls` is explicitly activated**, and routes for the existing
User/Audit services that keep their HTTP defaults. This is a source
candidate, not a live TLS connection or environment cutover.

To activate only in a disposable, operator-controlled TLS lab, both
`SPRING_PROFILES_ACTIVE=gateway-mtls` and
`AETHERIS_GATEWAY_UPSTREAM_MTLS_REQUIRED=true` must be present.
Other required external settings:

- `AETHERIS_USER_BACKEND_URI=https://user-service:8081`
- `AETHERIS_AUDIT_BACKEND_URI=https://audit-service:8083`
- `AETHERIS_GATEWAY_TLS_CLIENT_KEY_STORE_PATH` absolute non-symlink
  operator-mounted PKCS#12 keystore, nonempty; on POSIX systems no
  group/others read or write permissions
- `AETHERIS_GATEWAY_TLS_CLIENT_KEY_STORE_PASSWORD` nonblank secret
- `AETHERIS_GATEWAY_TLS_TRUSTED_CA_CERT_PATH` separate, absolute
  readable non-symlink operator-managed trusted server CA certificate
- `AETHERIS_ORCHESTRATOR_URI` must remain exactly
  `http://orchestrator-service:8090` in this narrow candidate.
  Reject unintended TLS destinations receiving a global client identity.

The real Spring Cloud Gateway `httpclient.ssl` properties map these
external inputs to native Reactor Netty client keystore, trusted CA
certificates and `use-insecure-trust-manager=false`. No private material
is in YAML, PR, logs or image; keystore password is supplied externally.

The new `GatewayOutboundMutualTlsPreflight` Spring component rejects
incomplete or unilateral opt-in, unknown/malformed boolean values, any
HTTP User/Audit route while the mode is on, unexpected HTTPS targets,
query/path/user-info/fragment in service origins, missing/empty/symlinked
external files and a too-permissive client keystore. It does **not**
attempt to load the keystore cryptographically; the native HTTP client
is responsible for actual key and CA loading.

Real Java JUnit tests cover the above preflight and legacy default
compatibility, and exact-head CI includes them with the inherited
User/Audit servlet SPIFFE attribute tests, real Identity-issued JWT
interoperability, PostgreSQL 16 role/migration/restore rehearsals, and
Stage45 NO_GO. Tests use **synthetic non-key placeholder files** only;
their success cannot be treated as proof of a valid key or trust chain.

## Essential limitations and external checks

1. User and Audit must be configured to serve HTTPS on 8081/8083,
   require actual TLS client certificates, use restricted trust anchors,
   and populate the standard servlet certificate attribute. This PR
   changes **neither service's server SSL settings**.
2. The Gateway must actually possess an operator-issued PKCS#12 client
   certificate with the exact allowed SPIFFE URI SAN and clientAuth EKU.
   This PR does not provision a certificate, certificate authority,
   SPIFFE control plane, client trust anchor or any secret.
3. Global HTTP client SSL settings can apply to *other* HTTPS routes;
   do not add third-party HTTPS routes in this mode without per-route
   certificate isolation and strict egress policy review.
4. Neither a native TLS handshake, hostname verification, certificate
   expiry/revocation nor real running Gateway-to-User/Audit HTTP request
   has been tested. The backend servlet MockMvc checks from #343 only
   simulate a container certificate attribute; **no actual mTLS claim**.
5. The application stack still uses an older Spring Boot 3.3 / Cloud
   2023 baseline in this branch, pending separate maintained-version
   dependency remediation. Issue #249 remains a release concern.
6. Tenant/object grants, true workload attestation, JWT revocation,
   real migration approvals, and all nine external Stage45 release
   attestations remain unresolved.

**Do not activate this profile in production.** There is no
production deployment, main-branch merge, live certificate installation,
physical device exercise or real tenant permission change here.
Keep PR DRAFT, NO_GO, and issue #251 OPEN.
