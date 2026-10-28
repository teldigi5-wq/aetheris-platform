# Actual Identity-to-Gateway/User/Audit JWT interoperability proof

**28 October 2026 · draft stacked on #340 · Stage45 NO_GO**

## What this test changes

This adds an executable, cross-module security contract based on the
**actual application Identity JwtService**, not independently hand-built
JJWT tokens in each downstream service's unit tests.

Hosted CI performs the following in a disposable working directory:

1. Invoke the REAL Identity JwtService with a freshly generated,
   in-memory RSA-2048 private signing key, configured issuer, pinned
   key ID, and synthetic ADMIN/API_CONSUMER account objects.
2. Write only the corresponding RSA **public** PEM (base64-encoded) and
   short-lived synthetic real signed JWTs to a private mode-0600
   temporary file. The private key is never saved. No real accounts,
   secrets, hardware, production signing key or persistent fixture exist.
3. The REAL reactive Gateway GlobalFilter verifies the resulting
   login/user/audit/orchestrator token values with a configured public
   key and pinned kid. Gateway must accept each exact-target route,
   reject unrelated recipients, reject consumer privilege escalation,
   remove spoofed caller tenant/identity headers and never forward
   rejected requests.
4. User and Audit run their real OncePerRequestFilter + MVC controller
   with the same Identity-issued JWTs and a mock persistence service.
   They must accept the matching privileged token, refuse wrong
   recipient and incorrect pinned kid, deny consumer attempts and never
   call the controller on rejected requests.
5. The test-only directory is deleted by a Bash EXIT trap even if
   the first Identity signer or a downstream module fails. Running the
   full Maven reactor without the explicit CI fixture path leaves these
   additional interoperability methods skipped by design; the
   required separate CI stage sets AETHERIS_INTEROP_MUST_RUN=yes to
   prevent silent skips.

This is a material improvement over separately constructed tokens:
JWT shape (JJWT role/scopes, signed subject/uid, issuer, aud and kid)
is now independently consumed from the real Identity implementation
by all three downstream authentication implementations.

## Important limitations

These tests do **not** start live Java HTTP servers, Redis,
RabbitMQ or a Kubernetes cluster, and do not prove TLS, service-to-service
workload identity, a real Identity persistence account grant, production
key custody, revocation, distributed tracing or tenant/object ownership.
User/Audit controller persistence collaborators are mocked only to
prove whether authorization reaches the controller. The existing
PostgreSQL 16 migration, role, signed tenant-binding and backup/recovery
rehearsals remain a separate synthetic CI step.

The ordinary Identity login path issues a token for exactly one configured
default audience; it cannot access all routes. The audience-scoped
internal issuance method is package-private and disabled by default.
**No new token exchange HTTP endpoint is authorized or created here.**
Additional real workload authentication and user-to-resource ownership
must be independently engineered before a token exchange can be enabled.
Do not use role claims or matching numeric User.id and IdentityAccount.uid
as tenant ownership proof.

No protected main merge, production secret, deployment, migration,
external signer, physical device or real environment was changed.
**Issue #251 OPEN · nine release attestations outstanding · Stage45 NO_GO.**
