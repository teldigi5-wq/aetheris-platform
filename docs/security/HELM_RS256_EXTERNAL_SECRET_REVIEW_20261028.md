# Aetheris Helm RS256 external-key reference candidate — 28 October 2026

**Review-only; stacked on PR #339; Stage45 NO_GO; no cluster or secrets touched.**

## Scope

The existing `aetheris` Helm chart has two explicit JWT modes via
`authJwt.mode` in `values.yaml`. The default remains **legacy**, which
creates the chart-managed HMAC Kubernetes Secret and now maps the signing
secret consistently to all four Java authentication services, including
the User and Audit backends. All prior local-demo and non-local safety
gates for Redis, PostgreSQL, RabbitMQ, image digests, and network policies
remain active.

The **rs256** mode is opt-in and requires ONLY non-secret chart values:
issuer, Identity primary recipient, three distinct route audiences,
active pinned key ID, and the **names of two externally managed Kubernetes
Secrets**. The chart does not create those external Secrets, embed a
private PEM in Helm values, produce the PEM in `helm template` output, or
include the legacy HMAC JWT secret in rendered resources.

The external signer Secret must be a namespaced, pre-provisioned Secret
with key `AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM`, containing an
operator-protected PKCS#8 RSA private key. **Only the Identity Deployment
receives this secretKeyRef.** The external verifier Secret must contain
`AETHERIS_JWT_RS256_PUBLIC_KEY_PEM`, and if a staged key ID is
configured it must also contain `AETHERIS_JWT_RS256_STAGED_PUBLIC_KEY_PEM`.
Gateway, User and Audit receive verifier-only public key references;
they never receive the private key or a legacy HMAC key in RSA mode.

The chart deliberately does not install the Secret resources, create
service accounts for a secret operator, declare any KMS, or assert that
Kubernetes Secret environment-variable injection is hardware-backed
custody. Private keys are still loaded into the Identity process and may
be exposed to node administrators or privileged container inspection.
This is **configuration isolation, not production key custody proof**.
Kubernetes PodSpec/configuration may reference a Secret name and key; it
must never contain the key contents.

## Fail-closed template behavior

Helm `fail` gates reject unknown modes, mixed legacy and RSA settings,
missing/whitespace issuer or audiences, repeated recipients, unknown
Identity default recipient, malformed or duplicate key IDs, missing or
equal signer/verifier Secret names and incomplete RSA settings. Legacy
mode still follows the existing non-local demo-credential gate. RSA
mode does **not** require replacing the unused demo HMAC value in
`secrets.jwtSecret`; that value is not rendered or passed to workloads.

The real Java services independently validate RSA key material, signed
issuer and audiences; Helm cannot certify that an external Secret exists
or that its key matches the public key/ID. An unavailable or malformed
external key results in startup/reconciliation failure, never authority
downgrade to HMAC.

## Synthetic render validation only

The new `tests/test_helm_rs256_external_keys.py` invokes actual
`helm template` and `helm lint` without contacting a Kubernetes
cluster. It checks legacy rendering, RSA-only isolation of the signer
private-key reference, public verifier-only refs, staged rotation,
no rendered HMAC secret in RSA mode and malformed config refusal.
It supplies only synthetic Secret names/IDs and no real cryptographic
keys. All existing Helm demo-safety tests still run.

## Hard blockers before any real deployment

1. Owner-approved custody, namespace RBAC, Secret provisioning,
   inspection restrictions and rotation/revocation policies.
2. Coordinated issuer/resource cutover with backward-compatibility
   planning and real login/session migration.
3. Authenticated token exchange, workload identity and tenant/object
   authorization; a single login audience is NOT sufficient for all APIs.
4. Actual cross-service HTTP authorization, helm render plus operator
   provisioning tests in a disposal-only non-production cluster.
5. Complete historical migrations and external security attestations;
   issue #251 stays OPEN, all nine Stage45 gates remain NO_GO.

No production key, live Secret, cluster, database, physical device,
protected main branch or deployment was changed. **Draft / unmerged.**
