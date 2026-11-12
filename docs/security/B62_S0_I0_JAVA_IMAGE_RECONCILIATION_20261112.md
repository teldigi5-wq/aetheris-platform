# I0 Java image reconciliation (B62-F checkpoint × canonical) — 12 November 2026

**State:** coded on `integration/b62f-checkpoint-20261112`; CI-only verification pending. Not merged, not deployed. Release remains `NO_GO`.

## Conflict

Merging canonical `3a82512` into the B62-F checkpoint `2554378` conflicts in the four Java service Dockerfiles:

| Line | Images | Evidence |
|---|---|---|
| B-line (B52/B54) | Amazon Corretto `21.0.13-al2023` / `-al2023-headless`, plus a pinned AL2023 `dnf upgrade` | `docs/security/JAVA21_CORRETTO_B52_20261026.md` records Corretto **21.0.13.0.10** |
| Canonical (#412) | Eclipse Temurin `21.0.13_9-jdk/jre-noble`, exact index digests | `docs/java-container-trust-20261109.md`: 21.0.13.0.10 does not resolve CVE-2026-114820; Temurin 21.0.13+9 does, and the eMudhra roots are proven absent at runtime |

## Resolution

The **stricter** side wins. That is canonical Temurin 21.0.13+9 with the runtime trust proof.

This is an exception to the readiness plan's "take B-line" default, because keeping the B-line pins would reintroduce a fixed CVE.

## Preserving the B-line requirements

- **B52 (exact digests, no floating or stale tags):** `tests/test_java21_corretto_oci_b52.py` now asserts the Temurin digests and forbids `amazoncorretto` and `21.0.12`.
- **B54 (patched runtime, non-root, scanner fails on serious findings):** `tests/test_b54_patched_runtime_contract.py` asserts the patched Temurin runtime stage, UID 10001, and a vulnerability scan of the built images.
- **B53 (image vulnerability scanning):** before this change, canonical scanned only the dashboard image. `.github/workflows/java-trust-runtime-proof.yml` now also scans all four built Java images, using checksum-verified Trivy 0.76.2.
  - Any HIGH/CRITICAL finding with a published fix fails the check.
  - Unfixed HIGH/CRITICAL findings are printed and uploaded as NO_GO evidence. They do not fail the check.
- **B51 (inventory):** `tests/test_java21_oci_inventory.py` asserts that only the patched Java images remain.

The B52/B53 workflows trigger only on retired B-branch names. They are left unchanged as historical evidence.

## Also corrected in I0

`tests/test_backend_jwt_wiring.py` was already failing at the B62-F checkpoint itself. It expected `secret: ${AETHERIS_JWT_SECRET}` without a default, but B5 deliberately allows an empty default so that RS256 mode can start.

The updated test is stricter than the original. It now also requires:
- HMAC as the default mode
- the 32-byte HMAC secret minimum in the filter
- fail-closed `IllegalArgumentException`s for invalid HMAC and RS256 configurations
