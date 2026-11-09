# Java 21 eMudhra trust mitigation — isolated container review (9 November 2026)

**State:** source-review candidate; not merged, deployed, or production-certified. This is post-roadmap security maintenance, not Stage 35.

## Scope and provenance

- Existing protected `main` and canonical development base: four retained Aetheris service Dockerfiles using Temurin `21.0.12_8`. The already-merged dashboard Node 22.23.4 / NGINX 1.31.8 / Alpine zlib 1.4-r0 changes and two-cycle gateway recovery proof remain intact.
- Earlier [draft PR #309](https://github.com/teldigi5-wq/aetheris-platform/pull/309) has 47 changed paths and diverges from current `main`; do not merge wholesale. Its historical Corretto image proof logs showed `21.0.13.0.10`, whereas vendor version `21.0.13.0.11` explicitly resolves CVE-2026-114820. Prior source tests are not transferrable to this candidate.
- This review instead pins the official patched **Eclipse Temurin 21.0.13+9** JDK/JRE Ubuntu Noble multi-platform index digests for all four core containers without changing Maven checksum verification, non-root UID/GID 10001, application Java sources or ports.
- JDK index: `sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d`.
- JRE index: `sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f`.

## Sources

- Adoptium official patched Temurin release: https://adoptium.net/news/2026/10/temurin-october-2026-emudhra/
- Official image-index details: https://hub.docker.com/layers/library/eclipse-temurin/21.0.13_9-jdk-noble/images/sha256-37c74ad0b4ad8a5ec6c65c0f1e9bbd88486199ddd906dd04575ef7b7356e4dc2 and https://hub.docker.com/layers/library/eclipse-temurin/21.0.13_9-jre-noble/images/sha256-db724162e4ff5f71fa0a2815026314d9e5e0e9a139f49be1d4717e6930e93341
- Oracle security alerts: https://www.oracle.com/security-alerts/alert-cve-2026-114820.html and https://www.oracle.com/security-alerts/alert-cve-2026-91403.html

## Fail-closed acceptance

- [ ] Exact-source unit tests and core dependency/UID policy checks PASS.
- [ ] Each of four images builds from pinned digest, exposes actual JVM `Temurin-21.0.13+9` and readable `cacerts`.
- [ ] Actual runtime truststore rejects the affected emSign ECC G3 and AAA cross-sign SHA-256 fingerprints; the test must not rely solely on alias names or fail if other legitimate emSign roots are present.
- [ ] Independent Java HTTPS positive control succeeds and the two affected-chain hosts fail specifically due to certificate validation (network/DNS outage is INCONCLUSIVE/FAIL, not a false PASS).
- [ ] Applicable build, security-runtime, deployment, recovery, CodeQL and Stage 27 checks succeed on exact PR head.
- [ ] Isolated promotion via canonical development and a separate protected-main PR after review; no bypasses, no bulk merge of PR #309.

## Remaining risk and release boundary

This candidate updates the **four application container image stages only**. The host toolchain/CI pins (`.java-version`, GitHub setup-java, first-boot contract) still reference Java 21.0.12 and must be assessed in a separate coherent upgrade; never call them patched merely because the Docker images are. Real deployed images, host JDK trust, JVM override truststores, actual gateway HTTPS/proxy validation, certificate/credential rotation and owner-PC checks remain pending. Physical PC status `BLOCKED_PENDING_HARDWARE`; overall release `NO_GO`. The AI runtime repository is a separate certified source owner and is not changed here.
