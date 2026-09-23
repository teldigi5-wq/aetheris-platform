# Engineering Verification Checklist

Use this checklist before calling an Aetheris change complete. It keeps portfolio claims tied to evidence and makes demos easier to reproduce after the split between `aetheris-platform` and `aetheris-ai-runtime`.

## Ownership first

- [ ] Identify whether the change is platform-owned, runtime-owned, or a cross-repository contract/integration change.
- [ ] Runtime-owned source roots are not copied back into `aetheris-platform`.
- [ ] Platform code does not silently depend on an unversioned or mutable runtime source checkout.
- [ ] A runtime `main` revision is not described as platform-certified unless the certification reference has been deliberately advanced with evidence.

## Build and startup

- [ ] Maven build completes for the affected platform Java modules.
- [ ] Frontend build completes when dashboard code changes.
- [ ] Platform core builds with runtime-owned source absent.
- [ ] Runtime-owned builds execute in `aetheris-ai-runtime` when runtime code changes.
- [ ] Docker images/build contexts resolve from their declared source owner.
- [ ] `docker-compose.core.yml` starts the required platform-owned stack without crash loops when core-only behavior is under test.
- [ ] `docker-compose.integration-external.yml` resolves the intended external runtime when integration behavior is under test.
- [ ] Kubernetes manifests / Helm templates render successfully when deployment files change.

## Functional verification

- [ ] Authentication succeeds with valid credentials.
- [ ] Protected routes reject unauthenticated requests.
- [ ] Authorization rules reject insufficient roles/scopes.
- [ ] Expected gateway routes reach the correct platform service.
- [ ] Orchestrator-dependent routes cross the declared external-runtime boundary rather than a restored local source copy.
- [ ] Persistence works for the feature being tested.
- [ ] Redis-backed behavior works when relevant.
- [ ] RabbitMQ-backed event flow works when relevant.

## Failure behavior

- [ ] Invalid input returns a structured client error.
- [ ] Downstream timeouts fail in a bounded way.
- [ ] Retry behavior does not create uncontrolled request amplification.
- [ ] Circuit-breaker/fallback behavior is observable when enabled.
- [ ] Service startup/readiness/liveness probes behave as expected.
- [ ] Restarting a service does not silently corrupt state.
- [ ] Missing/unavailable external runtime fails explicitly; the platform does not silently substitute uncertified runtime source or invent successful execution.

## Observability

- [ ] Application logs contain enough context to diagnose the tested flow.
- [ ] Relevant metrics are exposed.
- [ ] Traces can follow a representative request across the service boundary being tested.
- [ ] Health endpoints reflect meaningful dependency state.
- [ ] No credentials, tokens or secrets appear in logs or evidence artifacts.

## Kubernetes / orchestration

- [ ] Platform-owned workloads become Ready.
- [ ] Services have valid endpoints.
- [ ] ConfigMaps/Secrets are referenced correctly.
- [ ] Resource requests/limits are present for maintained workloads.
- [ ] Replica changes behave as expected.
- [ ] Deleting a pod demonstrates self-healing where appropriate.
- [ ] Runtime workloads/artifacts are attributed to the runtime repository rather than represented as platform-local source.

## Security hygiene

- [ ] No secrets or local credentials are committed.
- [ ] New endpoints are reviewed for authentication/authorization requirements.
- [ ] Sensitive values are not returned in API responses.
- [ ] Dependency/configuration changes do not weaken existing security assumptions.
- [ ] Deterministic owner policy remains authoritative over model output where the runtime is involved.
- [ ] Live-money execution remains disabled unless a future separately approved evidence boundary explicitly changes that policy.
- [ ] SECURITY.md guidance still matches the system.

## Cross-repository certification

- [ ] The platform certification reference points to the intended exact runtime SHA.
- [ ] The referenced runtime SHA has the applicable runtime-owned CI/certification evidence.
- [ ] Platform external-integration/readiness checks pass against the intended runtime boundary when affected.
- [ ] A newer runtime SHA is not adopted solely because it is newer.
- [ ] Hosted evidence is not relabeled as physical owner-PC proof.
- [ ] Physical-machine status remains `BLOCKED_PENDING_HARDWARE` until real target-hardware evidence exists.
- [ ] No production-activation or registry-publication claim is inferred from repository CI alone.

## Documentation

- [ ] README claims match current working behavior and current source ownership.
- [ ] Architecture docs are updated when service, repository or data-flow boundaries change.
- [ ] Architecture decisions are recorded when a meaningful design trade-off is introduced.
- [ ] Setup commands distinguish core-only from external-runtime integration where relevant.
- [ ] Release notes name the exact platform SHA and certified runtime SHA when both are in scope.
- [ ] Known limitations are stated instead of hidden.
- [ ] Historical stage documents are not rewritten merely to make current topology look simpler; current docs should explain the transition instead.

## Interview-ready evidence

Before demonstrating a milestone, be able to explain:

1. The problem the change solves.
2. Which repository owns the implementation.
3. Why the architecture was chosen.
4. At least one alternative and its trade-off.
5. One failure mode and how the system handles it.
6. How the behavior was verified.
7. What remains unverified, especially physical-machine behavior.

> The purpose of this checklist is not to claim production certification. It is to keep project claims honest, repeatable and technically defensible across both repositories.
