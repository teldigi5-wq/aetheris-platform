# B60 — Explicit bounded RS256 public-key overlap and retirement

**26 October 2026 — DRAFT stacked after [B59 PR #293](https://github.com/teldigi5-wq/aetheris-platform/pull/293); no production signing, real user tokens, deployed databases, protected main, CI release gating, registry images, key store, or system integrations altered.**

## Design objective and narrow changes

B59 introduced an opt-in 3072+ bit RS256 signer private to Identity, with one pinned **public key** in the actual Gateway/User/Audit API verifiers, plus a strict `kid` and RS256 algorithm. B60 adds an optional **second pinned public verification key** solely to Gateway, User and Audit to rehearse a controlled public-key rollover.

- The current `AETHERIS_JWT_KID` and `AETHERIS_JWT_RSA_PUBLIC_KEY_X509_B64` remain mandatory in RS256 mode.
- New optional **pair** `AETHERIS_JWT_PREVIOUS_KID` and `AETHERIS_JWT_RSA_PREVIOUS_PUBLIC_KEY_X509_B64` must be **both specified or both empty**. Distinct, lowercase allowlisted kid values; no duplicate signing key material, invalid/weak (under 3072-bit) RSA, malformed public-key encoding or oversized metadata are accepted. Startup refuses conflicting or half-applied changes.
- RS256 API verifiers try only the explicitly installed current key, then if that signature cannot validate, the explicitly installed previous key. The signing key that verified is tracked; the JWT's signed `kid` must identify **that exact key**, not simply be a known value. A previous private key cannot impersonate the current signing epoch by labeling a token with the new kid.
- Every successful signature still must carry exactly `iss=aetheris-identity` and singleton `aud=aetheris-api`, proper expiration/issued-at, positive integral Identity UID and the existing ADMIN/scope/route policy. HMAC does not become a fallback when RS256 is selected.
- **Retirement is explicit:** remove BOTH previous-key settings and restart all relevant services; tokens signed with the old private key immediately fail (401). No automatic key-fetch URLs, dynamic JWKS, untrusted header-driven key lookup, external keystore calls or live secrets.

## CI design and measured evidence scope

The B60 exact-head CI reuses B59's real four-module Maven tests and previous 72 HMAC-mode HTTP auth/ownership/workload checks, plus its 32 actual single-RSA-mode tests.

An additional **one Identity test** creates two unrelated **ephemeral** 3072-bit RSA keypairs in JVM memory; it writes only the two PUBLIC X.509 DER keys and short-lived synthetic JWTs into `identity-service/target/b60-rotation`. This includes a *malicious previous-key signature with current-key kid*, unknown/untrusted key, wrong issuer and workload-only audience. The two private keys are never written to disk or passed to Gateway/User/Audit. Nine real Gateway/User/Audit Java contract tests exercise overlap acceptance, invalid/duplicate/partial key configuration and post-retirement refusal.

Then two isolated CI-only Compose overlays extend the existing internal Docker network and tmpfs database: first install both public keys in the actual non-root Java services and check **28** direct backend and routed Gateway HTTP outcomes (4 paths × 7 statuses); then remove the old public key from each running service and check **12** additional HTTP outcomes (4 paths × 3 statuses). The read-only probe never receives private keys, real identity information, HMAC signing material or a Docker socket. All resources are destroyed at workflow completion.

**CI success is accepted only at the final exact PR head, and only if both phases and the inherited tests pass.** An initial implementation test run does not alone certify the final documentation revision.

## Bounded overlap is operator-controlled, not time-enforced

**There is no automatic TTL-based removal of previous public keys in B60.** An operator-controlled timeline must publish the new PUBLIC verification key and allow overlap only long enough for the actual maximum token TTL + authorized clock skew, then remove the previous public key from every API verifier. The code intentionally fails closed on malformed configurations but does not claim to certify real-world rollout scheduling, revocation, session continuity or emergency compromise response.

Production still requires a real issuer private-key custody/KMS/HSM solution (B59 used base64 configuration as a prototype), approved `kid` publication and public verification-key transport, key expiration and policy enforcement, session/browser/mobile/Syntra runtime compatibility, coherent service rollout, automated deployment evidence, support for stuck clients, old-token rejection, rollback and incident response. A previous signing key compromise is **not** fixed until all corresponding verifying public keys are removed; simply issuing new tokens is insufficient.

## Outstanding NO_GO gates

Issue #251 still requires real authenticated resource ownership/tenant policy, trusted workload identities, actual key management and cloud network bypass protections. Issue #242 requires an operator-provided representative historical database and restore. #255 and #259 require production image/SBOM/provenance acceptance, and the nine runtime certification gates and Stage45 remain unapproved. No deployment or merge is authorized by B60 synthetic CI alone.
