# Opt-in Gateway mTLS client-certificate workload identity — review only

**28 October 2026 · draft stacked after #342 · Stage45 NO_GO**

## Current trust issue

The Aetheris Gateway independently verifies signed user JWTs and forwards
HTTP requests to User and Audit. The backends independently verify the JWT
but cannot currently prove that the calling network workload was the Gateway.
A forged forwarded header is NOT proof. Existing ingress NetworkPolicy
restricts source Pod labels in non-local Kubernetes environments, but a Pod
label or network origin alone is not cryptographic workload identity.

## Review-only implementation

The real `DirectApiJwtFilter` in **User** and **Audit** now has a
**disabled-by-default** additional mTLS client-certificate requirement.
When explicitly enabled, the filter refuses to initialize unless ALL of
the following operator-provided settings exist:

- `AETHERIS_BACKEND_GATEWAY_MTLS_REQUIRED=true`
- `AETHERIS_BACKEND_GATEWAY_SPIFFE_ID` is exactly one canonical
  `spiffe://trust-domain/ns/namespace/sa/gateway-service-account` URI,
  matching the intended Gateway workload
- `server.ssl.enabled=true` and `server.ssl.client-auth=need`
- `server.ssl.trust-store` or `server.ssl.bundle` identifies a separately
  reviewed non-default client certificate trust configuration
- A configured RS256 *public-key only* JWT verifier with nonempty pinned
  key ID and valid issuer/audience policy, **not shared-HMAC acceptance**

The filter relies on the **servlet container's verified** `jakarta.servlet.
request.X509Certificate` attribute, not `X-Forwarded-Client-Cert`,
`X-Aetheris-Gateway-Identity`, spoofed identity/tenant headers, or client
HTTP parameters. The leaf certificate must be currently valid, not a CA,
have DigitalSignature key usage and ClientAuth extended key usage, and
carry **exactly one URI SAN**, byte-for-byte equal to the configured
SPIFFE identity. It is a second requirement alongside the end-user signed
RS256 JWT and existing role, scope, audience and object authorization.
An unverified or wrong client certificate is denied **403** before bearer
authorization or MVC controller invocation. Wrong JWT remains denied **401**
even for a matching client certificate.

Actual User and Audit MockMvc servlet tests prove: legacy opt-out
compatibility; missing certificate; forged X-Forwarded-Client-Cert header;
wrong/expired/clientAuth-inappropriate/multiple URI SAN certificate;
valid matching certificate plus invalid user JWT; valid matching certificate
plus correctly signed pinned RS256 ADMIN JWT; and invalid TLS/identity
configuration refusing construction. The certificate attributes in those
tests are synthetic: **no real TLS handshake or CA chain validation occurs**.

## Missing requirements — no permission to enable this yet

This PR deliberately **does not** configure the Gateway's outbound mutual
TLS HTTP client, generate a server or client certificate, establish an
actual SPIFFE issuance/trust domain, install trust stores, edit Helm/Compose,
deploy containers, or perform live TCP/TLS tests. A configured certificate
attribute from a *properly authenticated TLS connector* is only meaningful
when operator-provided roots are private and restricted, the server HTTPS
endpoint is actually reached, certificate chain and key usage are checked by
the TLS stack, and any reverse proxy cannot fabricate the attribute.

The new opt-in setting will fail closed for existing HTTP-only Gateway ->
backend traffic until **both sides** have reviewed TLS certificates and
client/server trust configured. Do not flip this flag alone: that would
intentionally block protected routes. It is not a finished zero-trust
mesh, rotation/revocation system, workload attestation, tenant ownership
model, or production deployment blueprint.

No protected main merge, production environment/secret, Kubernetes object,
identity account, database migration, physical device or live client
certificate was changed. Identity.uid still is NOT proof of User.id
ownership; API_CONSUMER object access stays forbidden.

**Issue #251 OPEN · all nine Stage45 independent release gates outstanding · NO_GO.**
