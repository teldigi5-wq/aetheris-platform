# B51 — Java 21 October 2026 OCI digest inventory (pre-remediation)

**Review-only draft, 26 October 2026. Production security issue #255 remains OPEN.**

## Why the four Dockerfiles have not yet been edited

The B50-verified Platform source currently uses the SAME explicit old Eclipse Temurin 21.0.12_8 JDK and JRE immutable OCI manifest digests across Gateway, Identity, User and Audit. These pins are reproducible but are not the October 2026 Java 21.0.13 security baseline.

Eclipse Temurin's Linux JDK/JRE **21.0.13+9** binaries were released on October 22, 2026, but the public official Docker tag listings found during investigation continued to list **21.0.12.1_1** version-specific Ubuntu Noble tags. Published binaries do NOT prove matching Docker Hub multiarch OCI images have been built/published. Never guess a sha256 image digest.

Authoritative publisher references:
- https://adoptium.net/temurin/releases?version=21
- https://adoptium.net/installation/containers
- https://github.com/docker-library/docs/blob/master/eclipse-temurin/README.md
- https://github.com/adoptium/containers

## B51 delivered

1. A strict offline checker, `scripts/verify_java21_oci_inventory.py`, requires matching JDK and JRE from the official `docker.io/library/eclipse-temurin` repository, an exact public 64-character `sha256` digest for EACH, independently verified `linux/amd64` and `linux/arm64` platforms, and actual **OpenJDK 21.0.13** version strings. Duplicate/oversized/foreign evidence is refused.
2. Nine Python negative tests deliberately reject Java 21.0.12, incorrect version combinations, missing architecture, untrusted publishers, non-digested images and malformed/duplicated evidence.
3. The dedicated **GitHub-hosted runner** asks Docker's actual remote manifest service about the version-specific `21.0.13_9-{jdk,jre}-noble` tags and optional `21-{jdk,jre}-noble` aliases. An alias is accepted **only after its actual resolved digest is pinned**, the manifest includes both required architectures, and the image runs Java 21.0.13 with network disabled, privileges dropped and read-only filesystem. The JDK must also provide the expected `javac -version`.
4. If either audited image is absent, the workflow emits `B51_OCI_NO_GO_UPSTREAM_PATCHED_PAIR_NOT_VERIFIED` and a separate human-readable **BLOCKED** summary. The CI job can finish GREEN because the **inventory tooling worked**, NOT because Java #255 is patched. A successful published-image verification reports the two exact candidate digest references as **READY_FOR_DOCKERFILE_REVIEW**, also not a production release GO.
5. No Dockerfile, immutable image digest, Java application source, service security, named volume, secrets, deployment or protected main is changed. The current old pin is retained until a verified replacement is actually available.

## Required follow-on before #255 can be remediated

Once a genuine official patched pair has been verified, stage a **separate reviewable code-change commit** with those exact immutable digest references in all four application Dockerfiles. Validate the builder's `javac -version` and runtime `java -version` again, both supported platforms, Maven reactor, actual built containers, genuine Gateway/Identity/User/Audit HTTP smoke, image provenance/SBOM/advisory scan and documented rollback. Only consider updating `actions/setup-java` pins after verifying the corresponding distribution release is available. Never adopt a mutable tag alone, pretend a failed/blocked lookup is a verified digest, or use unreviewed Oracle JDK licensing.

Stage45 NO_GO; issue #255 OPEN. Independent #242 historical DB validation, #251 object authorization, #259 complete SBOM and all nine runtime certification gates also remain unapproved. B51 is supply-chain discovery, not production certification.
