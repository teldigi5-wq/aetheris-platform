# B59 — opt-in Identity-owned RS256 and public-only Gateway/User/Audit trust

**26 October 2026 · DRAFT, stacked on [B58 PR #292](https://github.com/teldigi5-wq/aetheris-platform/pull/292). No production release/merge, live key generation, credential rotation, or real user migration.**

## Design decision

The previous four Java services still share a single HMAC signing/verification secret, so a compromised Gateway/User/Audit verifier can theoretically mint indistinguishable ADMIN bearer tokens. B57 added signed issuer/audience validation, and B58 denied multi-audience cross-workload reuse, but neither removed the common symmetric signing capability. B59 implements a **strictly opt-in RS256 path** in the four real production-code services, without silently changing the existing default `hmac` path.

- **Identity** is the **only holder of PKCS#8 DER RSA private signing material** in the candidate. When explicitly configured for `rs256`, it signs actual JWTs with JJWT `RS256`, an allowlisted lower-case `kid` and the existing `iss`, exact API `aud`, integral user ID, role, scopes, issue and expiry claims. RSA modulus must be **at least 3072 bits**.
- **Gateway/User/Audit** receive **only X.509 SubjectPublicKeyInfo DER public verification material** via their own `AETHERIS_JWT_RSA_PUBLIC_KEY_X509_B64` environment configuration. Each requires a pinned configured `kid`, verified RSA signature, algorithm **exactly RS256**, signed expected issuer and API-only audience, then its original role, scope, UID, lifetime and route-specific checks.
- **Mode selection is explicit:** `AETHERIS_JWT_MODE=hmac` remains the existing default; `AETHERIS_JWT_MODE=rs256` accepts the RSA key and denies any configured nonblank legacy HMAC secret. Malformed, weak (including RSA 2048), missing, mixed-key, unknown-mode and malformed-`kid` configurations fail startup; tokens signed by another key, with an alternate `kid`, with HS256, wrong issuer/audience, expiry or signature corruption fail authorization.
- No RSA key file, JWT signing material, or credentials are committed. The application YAML uses empty-safe placeholders but **HMAC startup still requires a valid 32+ byte secret**. Identity's private-key configuration is absent from every Gateway/User/Audit source/config file; the public keys are not trusted to sign.
- **IMPORTANT:** No automatic HMAC-or-RSA dual acceptance is implemented. This prevents a legacy HMAC signing secret becoming a fallback bypass while a team believes asymmetric verification is in force.

## B59 exact-head test contract

1. Run source/security policy regression tests and full four-service Maven tests in CI at the exact PR head.
2. Execute **real `IdentityRs256B59ContractIT`**, which generates a **fresh, synthetic RSA-3072 keypair in JVM memory** and uses actual `JwtService.issue()` to mint an ADMIN token and seven attack variants. It writes only an X.509 **public** key, short-lived JWTs and a synthetic marker into the disposable `identity-service/target/b59-rsa-contract` directory; no private-key material is retained.
3. Run **Gateway, User and Audit genuine RS256 verifier integration tests** using that public key and tokens, proving the valid authorized ADMIN request and refusal of wrong issuer, audience, key, `kid`, expired/tampered and legacy HMAC tokens.
4. Keep all B58 **real Docker HMAC-mode** security results intact: 16 workload audience + 26 real HTTP authentication + 30 signed nonowner-ID boundary checks, backed by non-root Corretto 21.0.13 services and isolated PostgreSQL tmpfs on an internal Docker network, with no host ports.
5. Use `tests/security/docker-compose.b59-rsa-overlay.yml` **ONLY inside CI** to restart the same disposable Gateway/User/Audit services in RS256 mode with **public-only RSA material and an empty HMAC secret**. An unprivileged read-only probe in the internal network performs **32 real HTTP assertions** (four direct/Gateway paths × eight cases). Only authentic Identity-signed ADMIN access returns 200. All seven invalid variants return 401. Network/port/privileged/private-key topology is explicitly checked before startup.
6. Always destroy CI-owned disposable containers, temporary PostgreSQL storage and network. The RSA test private key stays process-local and the generated public/test-token fixtures remain disposable; no registry image push, production rollout or signed attestation is performed.

## Production rollout/rotation requirements — not performed here

This candidate does **not** activate RS256 on actual users or agents; a production migration would require:
- A real KMS/HSM/secret manager for an issuer-owned private key, **never a committed PEM or environment variable copied into an app repository**. Base64 env is a bounded prototype injection mechanism, not a preferred full production key-management architecture.
- Approved protected public-key distribution, pinning and rotation of `kid` across all three API verifiers, controlled provisioning and cache freshness, rollback of the **complete synchronized Identity + Gateway/User/Audit release**, and a tested real-user authentication/session renewal protocol. The strict key/mode change intentionally rejects previously minted HMAC JWTs; never enable an indefinite permissive compatibility fallback.
- Asymmetric issuer-key cutover and a separate worker / Syntra audience contract. A token minted for `aetheris-api` is **not** a general machine-to-machine Syntra workload credential; distinguish service identity and authorization from API user identity.
- A properly bounded two-public-key overlap and revocation policy (previous B58 test-only rehearsal is **not** wired into real verifier mode yet), explicit signature-validation and provider trust-store review, audit/redaction rules, staging/mobile/browser compatibility, and actual rollback.
- Object/tenant ownership checks and resource mapping; signed `uid` is not itself a verified User-row owner.

## Remaining release blockers

Issue #251 direct-backend resource ownership, formal key rotation, mTLS/workload identity and Kubernetes CNI validation remain open. So do historical DBA recovery #242, final image acceptance #255, SBOM/provenance release acceptance #259, Stage45 **NO_GO** and nine uncertified runtime gates.

**A passing B59 draft CI run is proof only of the isolated tested opt-in RSA candidate. It is not production readiness, live key rotation, certified zero-vulnerability status, or a successful deployment.**
