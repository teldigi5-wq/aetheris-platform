# Aetheris Deployment & Release Runtime Proof

This post-roadmap proof validates release identity, hosted Docker Compose deployment, controlled outage detection, same-image recovery, and state preservation for the Aetheris platform runtime.

It is **Aetheris infrastructure work**. It does not redefine Syntra's assistant/persona role.

## What the proof does

The GitHub Actions workflow builds the five core application images from the exact checked-out revision and starts the normal Aetheris dependencies and services with Docker Compose. The proof then:

1. binds the run to the exact Git commit SHA;
2. records SHA-256 hashes for `docker-compose.yml`, the proof contract, and each core service Dockerfile;
3. records immutable Docker image identities for gateway, identity, user, audit, and orchestrator services;
4. writes a machine-readable release manifest and verifies its SHA-256;
5. verifies gateway, identity, and user-service health;
6. creates a real authenticated Aetheris identity through the gateway and performs a protected user read;
7. deliberately stops the gateway and proves the outage is observable;
8. proves protected traffic is unavailable while the gateway is stopped;
9. starts the gateway again;
10. verifies the recovered gateway uses the exact same Docker image identity recorded before the outage;
11. proves the protected read recovers; and
12. proves the persisted user state is unchanged across the recovery.

## Evidence

The workflow produces:

- `deployment-release-manifest.json` — exact revision, service image IDs, and source SHA-256 values;
- `deployment-release-runtime-report.json` — all 17 checks, baseline/final state, manifest digest, and truth boundary;
- Compose service/image snapshots;
- a Docker resource snapshot; and
- failure logs when the proof fails.

These are published as the `deployment-release-runtime-proof` GitHub Actions artifact.

## Relationship to existing release hardening

Existing release-hardening and reproducibility controls validate static release policy, dependency locks, deterministic artifacts, manifests, and source evidence. This proof is complementary: it validates that an exact-revision candidate can actually run, fail in a controlled way, and recover onto the same verified runtime image while preserving state.

## Truth boundary

Evidence class: `HOSTED_RUNTIME`.

This proof does **not** claim:

- a production AWS, Azure, GCP, Vercel, Kubernetes, or other provider deployment;
- blue/green, canary, cross-region, or zero-downtime certification;
- a provider-native rollback;
- production SLA, SLO, RTO, or RPO guarantees;
- target-PC performance; or
- physical-PC validation.

Physical-PC validation remains `BLOCKED_PENDING_HARDWARE`.

## Syntra / Aetheris boundary

Aetheris remains the platform/runtime: services, identity, APIs, persistence, policy, approvals, CI/CD, evidence, and runtime operations. Syntra remains the personal AI assistant/persona that can use those capabilities. This proof changes only Aetheris release/deployment assurance.
