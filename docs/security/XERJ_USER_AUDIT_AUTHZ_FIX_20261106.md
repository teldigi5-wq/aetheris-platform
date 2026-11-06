# Aetheris XERJ Candidate — independent backend authorization repair

Status: DRAFT SECURITY REVIEW. Not merged, not deployed, not published to XERJ, not release-certified.

## Exact-stack provenance
- Review child of existing issuer/audience security PR #229 at `b59f02ceadfdd0b98b32b09cd6bdeda483d75d9a`.
- Source vulnerable XERJ snapshot at protected main `57ad3e0e602efa6feb275328254151b959a3499b` is **unchanged** and must not be publicly approved or registered.
- Keep XERJ draft #376 on **HOLD**, Stage 45 **NO_GO**, and platform security issue #251 **OPEN** until independently verified.

## Threat / explicit authorization policy
The historic User and Audit APIs store/return names and email addresses. No tenant ownership mapping exists between the identity and user tables; merely comparing JWT `uid` to `users.id` would be a misleading access-control fix.

Fail-closed preview: **these global APIs are administrator-only**. Signed `ADMIN` role + explicit `users:read`, `users:write` or `services:read` scopes are required at both Gateway and direct service entrypoints. Self-registered `API_CONSUMER` and `DEVELOPER` roles cannot use these global APIs even if a valid signed token includes a broad scope. Other future service endpoints require a bearer by default; only health and servlet error handling bypass authentication.

Each backend verifies bearer signature (HS256 pinned), issuer, audience, expiry, issuance time, max lifetime and persisted positive UID. It does not trust `X-Aetheris-User-*` headers. Error responses contain no bearer/identity material. Duplicate bearer headers are rejected.

All four services require an externally supplied JWT key and all three Compose variants pass the same explicit variable to the backends; **the previous publicly known fallback key is removed**. This intentionally breaks unconfigured local startup until a high-entropy private key is configured. Do not commit the key or store it in reports.

## Security test scope
CI runs direct User/Audit mock HTTP requests without Gateway: anonymous, forged identity headers, consumer/developer role bypass, missing scope, wrong issuer/audience, expiry, malformed/duplicate bearer, valid scoped administrator, and write privilege separation. It also tests Gateway administrator policy, issuer/audience token issuance and Compose credential wiring.

## Unresolved release/security gates
- **The underlying data model has no verified tenant ownership.** Administrator-only global APIs do not establish tenant isolation; no multi-tenant public release is authorized.
- Shared HS256 key among services still permits any compromised verifying service to forge tokens. A later asymmetric RS256/mTLS design with independent key provenance and rotation is required before production authority.
- Existing PR #248 rate-limit spoofing, #250 network ingress, #346 real mTLS, and Runtime #23 remain separate and **unmerged**.
- Full Maven suite, disposable Docker/Kubernetes negative ingress, real Postgres/RabbitMQ transaction-consistent outbox, supply-chain/SCA audit, historical credential reuse and rotation, owner rights, and XERJ upstream licence review must be independently verified. A targeted passing CI is not a complete security audit.
- The old pinned XERJ 40-file scope refers to vulnerable code; a **new exact source pin**, independently reviewed, must replace it only after the security work is merged and verified through approved gates.

No source snapshots, credentials, deployment or external Hub candidate were modified by this review-only branch.
