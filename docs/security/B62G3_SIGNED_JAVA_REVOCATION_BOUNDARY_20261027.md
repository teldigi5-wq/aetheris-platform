# B62-G3 — Genuine Java/JJWT signed-token revocation gate proof

**27 October 2026 · REVIEW-ONLY · NOT INTEGRATED**. Based on B62-G2 PR #303 exact successful head `a02f17600275a920572112820fd491c5aef11c2a`. Code changes are restricted to the Java Identity **test source**, source safety guard, CI workflow, and roadmap. This is not a production app release, database migration, key rotation, or real emergency revocation service.

## Engineering rationale

B62-G1 demonstrated monotonic principal/kid revocation records in a disposable PostgreSQL database. B62-G2 demonstrated a strict test-only Python HMAC validator plus a separate read-only PostgreSQL lookup with restart/outage behavior. Before wiring a real Java Gateway/User/Audit filter to any shared revocation state, B62-G3 explicitly tests how genuine Java JJWT RS256 signature and claim validation can precede a bounded revocation decision. A Python simulation alone cannot certify the Java signing boundary.

## Scope

`B62G3SignedJavaRevocationBoundaryTest` is an isolated JUnit 5 reference implementation under `identity-service/src/test`, not a Spring bean, production verifier, live JDBC adapter, or deployed route. It generates ephemeral **3072-bit** RSA key pairs in memory and signs synthetic JWTs with actual `Jwts.builder().signWith(..., Jwts.SIG.RS256)`; never exports RSA private keys. JJWT's `parseSignedClaims` with a fixed verification public key, pinned issuer/audience and zero parser skew executes before any revocation callback. It additionally validates exact kid, RS256 algorithm, singleton audience, positive integral UID, role, expected scope and `iat`/`exp` freshness with a maximum one-hour signed duration. A test-only read-only `ReadonlyRevocationDecision` interface receives **only validated** UID, issuer, verified kid and signed issued-at epoch; no raw token or caller-selected SQL goes to the lookup.

Negative tests cover signature tampering, a second untrusted RSA private key, relabelled kid, foreign issuer, foreign and dual audiences, zero/fractional UID, wrong role, future `iat`/`nbf`, expiration, overlong JWT and malformed input, demanding zero lookup calls. For valid cryptographically signed JWTs it demonstrates a monotonic synthetic principal cutoff, independent signer/kid block and denial on reader exception, timeout or false. The three named gateway/user/audit loops are **simulated consumers**, not actual Java service filters. B62-G2's separate real isolated PostgreSQL restart and outage proof remains the datastore evidence.

The exact-head CI first executes all existing B62-G1/G2 source and offline tests, then the complete Java four-module reactor and finally explicitly runs the new signed RS256 JUnit boundary test. It does **not** represent real PostgreSQL JDBC access, Docker HTTP replay, true cross-node JWT revocation, physical-device testing or production truststore validation.

## Release blockers — NO_GO

**NOT INTEGRATED**: Gateway, User, Audit and Identity still have no deployed shared revocation lookup. Before any production integration: secure owner approval, complete historical database inventory and restore/migration approval under #242, authenticate separate read-only service identities and tightly limited emergency writers, add signed-claim-to-principal binding, verify actual service-owned resource authorization under #251, enforce read-after-write on a trustworthy primary without stale replicas, bound timeout/backpressure, deny on every store fault, harden concurrent session revocation, rehearse rollback without restoring compromised signer trust, and independently review certificate chains / eMudhra Java truststore release PRs #305 and #306. The certificate-remediation drafts need reconciliation into one reviewed release path; this test branch does not change them.

Nine runtime certification gates and Stage45 remain **NO_GO**. A successful CI test here is only bounded evidence, not customer session revocation, key custody attestation, or authorization to merge/deploy.
