# Security Runtime Proof

This post-roadmap capability pass adds **hosted runtime security evidence** for controls already implemented in the Aetheris core platform.

It is not a new numbered roadmap stage. The historical Syntra × Aetheris roadmap remains complete at **Stage 34 / 34**, and there is still **no Stage 35**.

## What this proof exercises

The workflow boots the real Docker Compose services on a GitHub-hosted Ubuntu runner, creates a synthetic API consumer through the gateway, attacks the authentication/authorization boundary, and inspects only that synthetic account's derived credential state in PostgreSQL.

The proof verifies that:

1. gateway and identity-service are healthy before the exercise;
2. registration returns access/refresh credentials and API_CONSUMER context;
3. a protected route rejects anonymous access;
4. forged `X-Aetheris-*` identity headers cannot bypass authentication;
5. the legitimate API_CONSUMER access token can perform `users:read`;
6. forged `ADMIN` / `users:write` headers cannot upgrade that token's authorization;
7. a signature-tampered access JWT is rejected;
8. an opaque refresh token cannot be reused as an access bearer token;
9. the synthetic account password is stored as a BCrypt cost-12 hash, not plaintext;
10. raw refresh bearer credentials are absent from persisted refresh-token values;
11. the active refresh credential is represented by its SHA-256 hash;
12. refresh rotation returns a distinct replacement credential;
13. the original refresh-token hash is revoked after rotation;
14. the replacement SHA-256 hash is distinct and remains active;
15. replay of the already-rotated raw refresh token is rejected.

The machine-readable contract contains **16 checks** because service readiness is recorded separately from the security controls.

## Files

- `.github/workflows/security-runtime-proof.yml`
- `tools/security_runtime_smoke.py`
- `build-evidence/runtime/security-runtime-contract.json`
- generated workflow artifact: `build-evidence/runtime/security-runtime-report.json`

The workflow also captures sanitized Compose/account/token-state evidence. It never writes the synthetic user's password, access JWT, or raw refresh tokens into the evidence artifact.

## Why this is stronger than configuration-only evidence

The repository already documents JWT validation, trusted gateway identity propagation, role scopes, BCrypt password hashing, SHA-256 refresh-token storage, rotation, and revocation. This pass does not merely parse that source or configuration.

It sends hostile requests through the live gateway and then queries the real PostgreSQL state created by the running identity service. That proves the checked control behavior at runtime while preserving a narrow evidence claim.

## Important architecture boundary

The user service is treated as an internal service behind the gateway. This proof therefore validates the public gateway boundary and does **not** claim that exposing internal service ports directly to untrusted networks is safe. Production network segmentation remains a deployment responsibility.

## Truth boundary

Evidence class: `HOSTED_RUNTIME`  
Capability: `security`  
Environment: GitHub-hosted Ubuntu + Docker Compose  
Physical-PC validation: `false`

This does **not** constitute a penetration test, formal security audit/certification, production secret-management or KMS validation, supply-chain certification, load/DoS testing, Kubernetes security validation, internet-exposed deployment validation, or target-PC validation.

The physical status therefore remains:

`BLOCKED_PENDING_HARDWARE`

Hosted CI remains repository evidence and is not a substitute for validation on the owner's future physical machine or a professional production security assessment.
