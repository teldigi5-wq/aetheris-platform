# Actual disposable Netty mutual TLS transport — security candidate

**28 October 2026 · staged after PR #344 · Stage45 NO_GO**

## Goal and actual tests

PR #343 verified the User/Audit servlet's synthetic client-certificate attribute;
PR #344 introduced the Gateway's opt-in native outbound PKCS#12 client TLS
and CA trust profile, without a network handshake.

This stage adds a real **TCP + TLS** socket test using Reactor Netty (the
transport underlying Spring Cloud Gateway) and temporary OpenSSL X.509
certificate issuance in a single hosted, disposable CI runner.

The synthetic test server binds **localhost, random ephemeral port, TLS only**,
with an X.509 server cert valid for DNS localhost, CA trust restricted to a
newly generated private throwaway CA, and **ClientAuth.REQUIRE**. The test
client loads a **real PKCS#12** file with a generated private key and
clientAuth X.509 certificate carrying precisely one SPIFFE URI SAN.

The client and test server validate:

1. **Positive:** a CA-trusted Gateway client certificate successfully completes
   a real TLS handshake and the server extracts the peer certificate from
   the Netty `SslHandler` authenticated TLS session; the exact SPIFFE URI
   identity is accepted with HTTP 200.
2. **No client certificate:** the server refuses the TLS handshake even with
   a forged `X-Forwarded-Client-Cert` HTTP request header.
3. **Wrong trust root:** a client configured to trust an unrelated synthetic
   CA refuses the server handshake; no HTTP request is authorized.
4. **Wrong workload identity:** a client certificate validly signed by the
   same CA but carrying an unrelated SPIFFE URI SAN completes transport TLS,
   then fails application workload-identity checking with HTTP 403.

Certificate material is generated with OpenSSL in a `mktemp` 0700
directory under `/tmp/aetheris-netty-mtls.*`, protected with `umask 077`.
Test private keys and PKCS#12 bundles have mode 0600; the PKCS#12 password
is generated in memory with `openssl rand`. A guarded Bash EXIT trap
deletes only the exactly matched disposable temp directory on test pass
or failure. No static certificate, credential, private key, TLS root,
production password, or internet host is accessed.

Ordinary four-module Maven tests intentionally skip the socket lab
unless explicitly given `aetheris.tls.lab.fixture`; CI separately
invokes it with `AETHERIS_TLS_LAB_MUST_RUN=yes` and requires all test
cases to execute. Hosted CI independently runs the full existing
Java/Helm/Compose/Pg16/signed-Identity-JWT security regressions.

## Truth boundary and still-open release work

**This proves real Netty TLS handshake semantics and certificate-scoped
SPIFFE identity checking in a disposable lab.** It does NOT prove the
real Spring Cloud Gateway route uses the tested client identity; the
lab starts a separate Netty HttpClient and HttpServer, not the entire
Spring Cloud Gateway application's routed traffic or actual User/Audit
Spring Boot HTTPS connectors.

Further real distributed work required:

- Configure real **User and Audit Spring Boot HTTPS servers** with
  `client-auth=need`, independent trust anchors and actual servlet
  verified-certificate attributes; exercise their direct backend routes.
- Prove actual Gateway route forwarding with the native outbound TLS
  profile, correct client keystore and server root, and strict hostnames.
- Test unauthenticated/incorrect-client traffic to those real HTTPS
  backends; verify identity, correct Identity-issued RS256 JWT audience
  and role/scope independent of transport.
- Validate certificate/key custody, rotation, revocation, service account
  identity issuance, namespace RBAC, blast radius and observability.
- Explicit tenant and Identity-to-User ownership binding, real migration
  approval, supported dependency upgrades, and nine independent Stage45
  production release attestations remain open.

Nothing has been merged to protected main, installed in any live cluster,
deployed to production, rotated, or migrated. PR draft/unmerged.
**Issue #251 OPEN, Stage45 NO_GO.**
