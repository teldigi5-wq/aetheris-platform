# Engineering Verification Checklist

Use this checklist before calling an Aetheris stage complete. It keeps portfolio claims tied to evidence and makes demos easier to reproduce.

## Build and startup

- [ ] Maven build completes for the affected Java modules.
- [ ] Frontend build completes when dashboard code changes.
- [ ] Docker images build successfully when container definitions change.
- [ ] Docker Compose starts the required stack without crash loops.
- [ ] Kubernetes manifests / Helm templates render successfully when deployment files change.

## Functional verification

- [ ] Authentication succeeds with valid credentials.
- [ ] Protected routes reject unauthenticated requests.
- [ ] Authorization rules reject insufficient roles/scopes.
- [ ] Expected gateway routes reach the correct service.
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

## Observability

- [ ] Application logs contain enough context to diagnose the tested flow.
- [ ] Relevant metrics are exposed.
- [ ] Traces can follow a representative request across service boundaries.
- [ ] Health endpoints reflect meaningful dependency state.
- [ ] No credentials, tokens or secrets appear in logs.

## Kubernetes / orchestration

- [ ] Workloads become Ready.
- [ ] Services have valid endpoints.
- [ ] ConfigMaps/Secrets are referenced correctly.
- [ ] Resource requests/limits are present for maintained workloads.
- [ ] Replica changes behave as expected.
- [ ] Deleting a pod demonstrates self-healing where appropriate.

## Security hygiene

- [ ] No secrets or local credentials are committed.
- [ ] New endpoints are reviewed for authentication/authorization requirements.
- [ ] Sensitive values are not returned in API responses.
- [ ] Dependency/configuration changes do not weaken existing security assumptions.
- [ ] SECURITY.md guidance still matches the system.

## Documentation

- [ ] README claims match current working behavior.
- [ ] Architecture docs are updated when service boundaries or data flow change.
- [ ] Architecture decisions are recorded when a meaningful design trade-off is introduced.
- [ ] Setup commands are still reproducible.
- [ ] Known limitations are stated instead of hidden.

## Interview-ready evidence

Before demonstrating a milestone, be able to explain:

1. The problem this stage solves.
2. Why this architecture was chosen.
3. At least one alternative and its trade-off.
4. One failure mode and how the system handles it.
5. How the behavior was verified.
6. What you would improve next for a larger production environment.

> The purpose of this checklist is not to claim production certification. It is to keep learning-project claims honest, repeatable and technically defensible.