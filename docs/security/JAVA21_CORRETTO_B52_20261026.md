# B52 — actual four-service Java 21.0.13 immutable patched OCI image cutover (review-only)

**26 October 2026. DRAFT stacked on B51 PR #285, which did not alter existing Dockerfiles.**

## Verified publisher / immutable base

The B51 [actual GitHub-hosted OCI verification run](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40285391771) checked official Amazon Corretto immutable **multiarchitecture index** digests, both linux/amd64 and linux/arm64 manifest entries, and executable OpenJDK 21.0.13 inside network-isolated containers:

- JDK builder: `docker.io/library/amazoncorretto:21.0.13-al2023@sha256:1a307926702559b0086a5c4d339eaafa9c992b3e8ba54201ac8ebbde31d22f0b`.
- Headless runtime: `docker.io/library/amazoncorretto:21.0.13-al2023-headless@sha256:212faf5f8f93d8f4854a3d89528834d11ad2d685de80721d4d24ac2fdd3e379e`.

Both are Docker Official Images maintained by the Amazon Corretto project. Their security baseline follows Amazon Corretto's October 20, 2026 release 21.0.13.0.10 (OpenJDK 21.0.13+9). Official source references:
- https://github.com/corretto/corretto-21/blob/develop/CHANGELOG.md
- https://github.com/corretto/corretto-docker
- https://hub.docker.com/_/amazoncorretto

**Availability is independently verified.** Unlike Temurin's older Noble Docker tags, these exact Corretto 21.0.13 OCI images exist, and their literal digests were copied only from the successful real registry/container verification output. This is a deliberate **vendor and base OS change**, not just a Temurin tag bump.

## Changed files and safeguards

All four `gateway`, `identity-service`, `user-service`, and `audit-service` Dockerfiles now use those SAME exact pinned builder/runtime index references. The existing Maven archive SHA-512 verification, two-stage build, non-root UID:GID 10001:10001 and service entrypoint are unchanged.

A dedicated test suite fails if any service has a different digest, a stale Temurin pin, floating tag, missing non-root identity or weakened Maven checksum verification.

The B52 GitHub-hosted actual-image workflow:
1. Verifies immutable source references and non-root UID controls.
2. Builds both patched Java 21.0.13 JDK stage and final headless runtime image for every service, checking `java -version`, `javac -version` and UID in real containers.
3. Runs real Maven module tests in those patched builder containers.
4. Reuses the existing isolated Audit Flyway test topology: one internal Docker bridge, zero published host ports, PostgreSQL tmpfs only, synthetic credentials and no automatic baseline. Starts actual patched Audit and requires exactly one successful Flyway V1 migration history row without BASELINE.
5. Deletes CI-owned isolated Docker resources only, unconditionally.

## Critical vendor/OS compatibility review

Amazon Linux 2023 uses RPM/dnf rather than Ubuntu apt. Real Docker builds must prove that the existing `groupadd`, `useradd`, `curl`, `tar` and `sha512sum` commands work without relaxing the application Dockerfiles. The runtime image is **headless Corretto**, not the previous Temurin JRE; footprint and OS package advisory coverage must be evaluated separately. Java's source/runtime compatibility also requires integration and application tests, not only `java -version`.

This change does NOT silently migrate existing service processes or volumes. No production host or customer database is accessed and no live credentials are rotated.

## Initial real-image CI verification

[Hosted Ubuntu Actions #40286517419](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40286517419) **completed successfully** on the implementation head `b2819f1d7461158ad835845899eeda52ae696c91`.

- **5/5** Python Dockerfile policy tests passed.
- **All four real patched Corretto JDK builders and AL2023 headless application runtime images built successfully**. The actual builder `java`/`javac` and runtime `java` output confirmed 21.0.13 for each service, and all four final images ran as non-root `10001:10001`.
- **79/79 real Maven tests** passed **inside the patched Corretto builder containers** (Gateway 23, Identity 14, User 25, Audit 17), with zero failures/errors/skips. These were not a repeat using the old 21.0.12 JDK.
- The dedicated test-only Compose topology had an internal-only bridge, no published host ports, a PostgreSQL tmpfs and no persisted database. The actual patched Audit app applied Flyway V1 and produced **exactly one successful version-1 SQL history row, zero BASELINE rows**. CI cleanup succeeded.

The first exploratory run #40286068208 failed because the AL2023 builder omitted `tar`. The next run #40286178888 exposed missing `gzip`; #40286342963 exposed missing `groupadd`/`useradd` in the minimal runtime. These are real vendor-switch compatibility findings, not security exploits. The final implementation adds **only `tar` and `gzip` to the JDK builder stage** plus `shadow-utils` for the named non-root runtime account. Existing Maven SHA-512 verification, UID/GID restrictions and source logic are unchanged.

This is **initial CI evidence only**: subsequent documentation changes require exact-final-head revalidation, and deployment/merge still require a separate operator/security release decision.

## Approval / NO_GO

A passing CI run will prove the **specific patched Java 21.0.13 Dockerfile source and tested container behavior**, not a full application release. Before merging/production, review final image SBOM/signature/provenance and OS vulnerabilities, the auth/core runtime and browser flow, service-to-service integration, architecture-specific tests, rollback plan and staging smoke. Issues #242 (historical database), #251 (ownership/security), #255 (release-image patch), and #259 (full dependency/SBOM) remain open pending signoff. Stage45 NO_GO and nine runtime certification gates remain unapproved.

**Keep PR draft and unmerged until exact-head CI proves the vendor change has not broken application functionality.**
