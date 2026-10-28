# Disposable real Spring Tomcat HTTPS User authorization test — 28 October 2026

**Security review candidate, stacked on draft PR #345. Stage 45 NO_GO.**

## What is actually tested

The existing Netty-only TLS socket test proved a real mutual-TLS handshake,
but it did not run an actual Java application's servlet authentication filter.
This stage uses a **minimal in-process Spring MVC application context with a
real embedded Tomcat HTTPS listener** bound to a random local port. It registers
the repository's **actual UserController and DirectApiJwtFilter** with a
mocked UserService (no production database, user identity or tenant data).

The CI-only certificate fixture is generated with the same disposable OpenSSL
runner as the earlier Netty test, plus an ephemeral server PKCS#12 keystore
and a CA-only Java truststore. TLS client authentication is configured as
`Ssl.ClientAuth.NEED` on the real Tomcat server connector, with a restricted
synthetic trust anchor. An ephemeral RSA keypair is generated in Java memory
only to sign test tokens; the signed user JWTs have an explicit issuer,
User-only audience and pinned `kid`.

This intentionally exercises actual TLS handshakes, Spring MVC controller
dispatch and the real User resource filter together:

- Matching CA-signed Gateway client certificate + exact SPIFFE SAN + pinned
  RS256 token with ADMIN/users:read => HTTP 200 and one controller call.
- Missing client certificate + forged `X-Forwarded-Client-Cert` and identity
  headers => TLS rejection, not a request to the protected controller.
- Same-CA client certificate with an unrelated SPIFFE identity + valid
  signed token => 403 before the controller.
- Valid TLS client + malformed bearer, unrelated audience or signed
  API_CONSUMER/users:write role escalation => 401; no service call.

All TLS private keys and keystores remain inside a 0700 `mktemp` folder
under `/tmp/aetheris-netty-mtls.*`, with 0600 private files and a per-run
random password. The Bash EXIT trap deletes the folder on completion or
failure. Normal Maven reactor test discovery skips socket tests without
the explicit lab property; hosted CI separately requires actual execution
with `AETHERIS_TLS_LAB_MUST_RUN=yes`.

## Limitations and remaining prerequisites

This is **a real HTTPS Tomcat plus application auth-filter test**, but
not the full deployed User Spring Boot service: the test supplies minimal
Spring MVC beans and a mock persistence service, not the Postgres, Redis
or AMQP runtime. It does **not** start the Spring Cloud Gateway application
or route through its actual Gateway route definitions; the Java HTTPS
test client calls the backend listener directly. Audit Spring Boot HTTPS,
Gateway routed mutual TLS, certificate renewal/revocation and live
Kubernetes identity trust remain separate acceptance stages.

The actual Identity-to-User durable ownership/tenant mapping still
does not exist; API_CONSUMER object access must stay forbidden. Framework
support upgrades, operational database migrations, backup/restore,
provenance and all nine independent Stage45 attestations remain outstanding.
Issue #251 stays open. No protected `main` merge, live secrets, production
deployment, physical devices, real customers or schema updates occurred.
